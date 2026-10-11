package ro.amadya.tables.internal

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.catalog.LineSelection
import ro.amadya.catalog.ProductCatalog
import ro.amadya.contract.model.AddRoundRequest
import ro.amadya.contract.model.GuestPaymentRequest
import ro.amadya.contract.model.GuestTable
import ro.amadya.contract.model.GuestView
import ro.amadya.contract.model.JoinTableResult
import ro.amadya.contract.model.PaymentIntent
import ro.amadya.payments.SessionPaymentState
import ro.amadya.payments.SessionPayments
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.ConflictException
import ro.amadya.shared.ForbiddenException
import ro.amadya.shared.LocalizedText
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.UnprocessableException
import ro.amadya.shared.toMoney
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

/**
 * Guests at a table, from the QR code: no login, a first name and a device token valid for one session (ADR 0010).
 * Guests can start a free table (the waiter confirms it), become owner of a table the waiter seated, or ask to join.
 */
@Service
@Transactional(readOnly = true)
class GuestService(
    private val tables: DiningTableRepository,
    private val areas: FloorAreaRepository,
    private val sessions: TableSessionRepository,
    private val guests: TableGuestRepository,
    private val pendingRounds: PendingRoundRepository,
    private val guestPayments: GuestPaymentRepository,
    private val catalog: ProductCatalog,
    private val payments: SessionPayments,
    private val bills: BillReader,
    private val views: TableViews,
    private val log: TableLog,
    private val staff: TablesService,
    private val settings: RestaurantSettingsApi,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    fun table(code: String, locale: Locale): GuestTable {
        val table = byCode(code)
        return GuestTable(
            tableLabel = table.label,
            restaurantName = settings.name(),
            enabled = qrEnabled(),
            occupied = sessions.findByTableIdAndStatus(table.id, SessionStatus.OPEN) != null,
            areaName = areas.findByIdOrNull(table.areaId)?.let { LocalizedText.fromMap(it.name)!!.resolve(locale) },
            logoUrl = settings.logoUrl(),
        )
    }

    /**
     * A free table: the guest starts its session and owns it; nothing reaches the kitchen until a waiter confirms it (or
     * the configured time passes). A table the waiter seated without an owner yet: the guest becomes its owner. Otherwise
     * the guest asks to join, and the owner or a waiter lets them in.
     */
    @Transactional
    fun join(code: String, rawName: String, locale: Locale): JoinTableResult {
        if (!qrEnabled()) throw UnprocessableException("tables.qr_disabled")
        val table = byCode(code)
        val name = rawName.trim().replace(Regex("\\s+"), " ").take(40).ifBlank { throw UnprocessableException("tables.name_required") }
        val now = clock.instant()
        val token = newToken()
        var session = sessions.findByTableIdAndStatus(table.id, SessionStatus.OPEN)
        val guest: TableGuestEntity
        if (session == null) {
            session = try {
                sessions.saveAndFlush(
                    TableSessionEntity(
                        tableId = table.id,
                        guests = 1,
                        waiterId = null,
                        waiterName = null,
                        openedAt = now,
                        origin = SessionOrigin.GUEST,
                        confirmedAt = null,
                        confirmDeadline = now.plus(confirmTime()),
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                throw ConflictException("tables.table_occupied", table.label) // somebody else scanned it a moment ago
            }
            guest = guests.save(TableGuestEntity(session.id, name, GuestRole.OWNER, GuestStatus.ACTIVE, hash(token), now))
            log.joined(session.id, name)
            staff.changed(session, TableChanged.Kind.SESSION_TO_CONFIRM)
            logger.info("Table {} started from the QR code", table.label)
        } else {
            val present = guests.findAllBySessionIdInOrderByRequestedAtAsc(listOf(session.id))
            if (present.count { it.status == GuestStatus.ACTIVE || it.status == GuestStatus.PENDING } >= table.seats + 2) {
                throw ConflictException("tables.table_full", table.label)
            }
            val hasOwner = present.any { it.role == GuestRole.OWNER && it.status == GuestStatus.ACTIVE }
            if (!hasOwner) {
                guest = guests.save(TableGuestEntity(session.id, name, GuestRole.OWNER, GuestStatus.ACTIVE, hash(token), now))
                log.joined(session.id, name)
                staff.changed(session)
            } else {
                guest = guests.save(TableGuestEntity(session.id, name, GuestRole.MEMBER, GuestStatus.PENDING, hash(token), now))
                staff.changed(session, TableChanged.Kind.JOIN_REQUEST)
            }
        }
        return JoinTableResult(token, views.guest(session, guest, locale))
    }

    fun view(token: String, locale: Locale): GuestView {
        val guest = guest(token, allowPending = true)
        return views.guest(sessions.findByIdOrNull(guest.sessionId)!!, guest, locale)
    }

    /** The round waits for the waiter's OK; nobody answering within the configured time confirms it automatically. */
    @Transactional
    fun sendRound(token: String, req: AddRoundRequest, locale: Locale): GuestView {
        val guest = guest(token)
        val session = openSession(guest)
        if (pendingRounds.countByGuestIdAndStatus(guest.id, PendingRoundStatus.PENDING) >= MAX_PENDING_ROUNDS) {
            throw ConflictException("tables.too_many_pending")
        }
        // Priced and checked now, so a sold-out product or a missing sauce is reported straight away.
        val priced = catalog.priceLines(req.lines.map { LineSelection(it.productId, it.quantity, it.modifierOptionIds.orEmpty(), it.notes) })
        val lines = priced.map { p ->
            PendingLine(
                productId = p.productId,
                quantity = p.quantity,
                optionIds = p.modifiers.map { it.optionId },
                notes = p.notes,
                name = p.name.toMap(),
                modifiers = p.modifiers.map { it.name.toMap() },
                total = p.unitPrice.multiply(BigDecimal(p.quantity)).setScale(2),
            )
        }
        pendingRounds.save(
            PendingRoundEntity(
                sessionId = session.id,
                guestId = guest.id,
                linesJson = lines.map(PendingLine::toJson),
                notes = req.notes?.trim()?.ifBlank { null },
                total = lines.fold(BigDecimal.ZERO) { acc, l -> acc + l.total }.setScale(2),
                deadline = clock.instant().plus(confirmTime()),
            ),
        )
        log.ordered(session.id, guest.name, ActorKind.GUEST, lines.map { LoggedItem(it.name, it.quantity) })
        staff.changed(session, TableChanged.Kind.ROUND_TO_CONFIRM)
        return views.guest(session, guest, locale)
    }

    /** The owner lets someone in or refuses them; waiters can do the same from their app. */
    @Transactional
    fun decide(token: String, guestId: UUID, approve: Boolean, locale: Locale): GuestView {
        val owner = guest(token)
        if (owner.role != GuestRole.OWNER) throw ForbiddenException("tables.owner_only")
        val session = openSession(owner)
        val other = guests.findByIdOrNull(guestId)?.takeIf { it.sessionId == session.id } ?: throw NotFoundException("Guest")
        staff.decideJoin(session, other, approve)
        staff.changed(session)
        return views.guest(session, owner, locale)
    }

    /**
     * Pays the whole rest or selected units with the payment provider, as a takeaway order is paid. The units are reserved
     * until the payment is captured, fails or expires; a previous payment of the same guest still pending is dropped.
     */
    @Transactional
    fun pay(token: String, req: GuestPaymentRequest, locale: Locale): PaymentIntent {
        val guest = guest(token)
        val session = openSession(guest)
        if (!settings.features().onlinePayments) throw UnprocessableException("tables.online_payments_disabled")
        if ((req.all == true) == !req.items.isNullOrEmpty()) throw UnprocessableException("tables.payment_all_or_items")

        val mine = guestPayments.findAllByGuestId(guest.id).map { it.paymentId }.toSet()
        payments.forSessions(listOf(session.id)).filter { it.id in mine && it.state == SessionPaymentState.PENDING }.forEach { payments.cancelPending(it.id) }

        val bill = bills.of(session.id)
        val allocations = if (req.all == true) emptyList() else bill.allocate(req.items!!.groupBy { it.lineId }.mapValues { (_, i) -> i.sumOf { it.quantity } }, locale)
        val amount = if (req.all == true) bill.payable else allocations.fold(BigDecimal.ZERO) { acc, a -> acc + a.amount }.setScale(2)
        if (amount.signum() <= 0) throw UnprocessableException("tables.nothing_to_pay")
        if (amount > bill.payable) throw UnprocessableException("tables.payment_exceeds_bill", bill.payable.toPlainString())

        val currency = settings.currency()
        val started = payments.startOnline(session.id, amount, currency, "${settings.name()} · masa ${staff.labelOf(session)}", allocations)
        guestPayments.save(GuestPaymentEntity(started.paymentId, session.id, guest.id))
        staff.changed(session)
        return PaymentIntent(
            paymentId = started.paymentId,
            provider = PaymentIntent.Provider.forValue(started.provider),
            amount = amount.toMoney(currency),
            clientSecret = started.clientSecret,
            publishableKey = started.publishableKey,
        )
    }

    /** The session a guest's live stream follows (also while they wait to be let in). */
    fun sessionOf(token: String): UUID = guest(token, allowPending = true).sessionId

    // ---------------------------------------------------------------- internals
    private fun guest(token: String, allowPending: Boolean = false): TableGuestEntity {
        val guest = guests.findByTokenHash(hash(token)) ?: throw ForbiddenException("tables.guest_not_allowed")
        val allowed = guest.status == GuestStatus.ACTIVE || (allowPending && guest.status == GuestStatus.PENDING)
        if (!allowed) throw ForbiddenException("tables.guest_not_allowed")
        return guest
    }

    private fun openSession(guest: TableGuestEntity): TableSessionEntity {
        val session = sessions.findByIdOrNull(guest.sessionId)!!
        if (session.status != SessionStatus.OPEN) throw ConflictException("tables.session_closed")
        return session
    }

    private fun byCode(code: String) = tables.findByQrCode(code)?.takeIf { it.active } ?: throw NotFoundException("Table")

    private fun qrEnabled() = settings.features().let { it.tables && it.qrOrdering }

    private fun confirmTime(): Duration = Duration.ofMinutes(settings.guestConfirmMinutes().toLong())

    private fun newToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    private fun hash(raw: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()))

    private companion object {
        const val MAX_PENDING_ROUNDS = 3L
    }
}
