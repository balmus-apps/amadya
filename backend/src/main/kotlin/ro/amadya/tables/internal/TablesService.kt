package ro.amadya.tables.internal

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import ro.amadya.contract.model.AddRoundRequest
import ro.amadya.contract.model.DiningTableAdmin
import ro.amadya.contract.model.DiningTableRequest
import ro.amadya.contract.model.FloorArea
import ro.amadya.contract.model.FloorAreaAdmin
import ro.amadya.contract.model.FloorAreaRequest
import ro.amadya.contract.model.FloorTable
import ro.amadya.contract.model.TablePaymentRequest
import ro.amadya.contract.model.TableSession
import ro.amadya.ordering.RoundLine
import ro.amadya.ordering.TableOrders
import ro.amadya.payments.InPersonMethod
import ro.amadya.payments.SessionPaymentCaptured
import ro.amadya.payments.SessionPaymentEnded
import ro.amadya.payments.SessionPayments
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.Actor
import ro.amadya.shared.ConflictException
import ro.amadya.shared.LocalizedText
import ro.amadya.shared.Money
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.UnprocessableException
import ro.amadya.tables.PreBillLine
import ro.amadya.tables.PreBillRequested
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.util.Locale
import java.util.UUID

/** Internal event: something about a table session changed; pushed to the waiter apps and the table's guests. */
data class TableChanged(val sessionId: UUID, val tableId: UUID?, val kind: Kind, val orderNumber: String? = null) {
    enum class Kind { UPDATED, ROUND_READY, CLOSED, SESSION_TO_CONFIRM, ROUND_TO_CONFIRM, JOIN_REQUEST }
}

@Service
@Transactional(readOnly = true)
class TablesService(
    private val areas: FloorAreaRepository,
    private val tables: DiningTableRepository,
    private val sessions: TableSessionRepository,
    private val guests: TableGuestRepository,
    private val pendingRounds: PendingRoundRepository,
    private val guestPayments: GuestPaymentRepository,
    private val activity: TableActivityRepository,
    private val orders: TableOrders,
    private val payments: SessionPayments,
    private val bills: BillReader,
    private val views: TableViews,
    private val log: TableLog,
    private val settings: RestaurantSettingsApi,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val eachInOwnTransaction = TransactionTemplate(transactionManager).apply { propagationBehavior = TransactionTemplate.PROPAGATION_REQUIRES_NEW }

    // ---------------------------------------------------------------- admin: areas and tables
    fun listAreas(): List<FloorAreaAdmin> = areas.findAllByOrderBySortOrderAsc().map { it.toAdmin() }

    @Transactional
    fun createArea(req: FloorAreaRequest): FloorAreaAdmin =
        areas.save(FloorAreaEntity(LocalizedText.of(req.name).toMap(), req.sortOrder ?: 0, req.active ?: true)).toAdmin()

    @Transactional
    fun updateArea(id: UUID, req: FloorAreaRequest): FloorAreaAdmin {
        val area = areas.findByIdOrNull(id) ?: throw NotFoundException("Floor area")
        area.name = LocalizedText.of(req.name).toMap()
        area.sortOrder = req.sortOrder ?: area.sortOrder
        area.active = req.active ?: area.active
        return area.toAdmin()
    }

    fun listTables(): List<DiningTableAdmin> = tables.findAllByOrderBySortOrderAscLabelAsc().map { it.toAdmin() }

    @Transactional
    fun createTable(req: DiningTableRequest): DiningTableAdmin {
        val label = req.label.trim()
        if (areas.findByIdOrNull(req.areaId) == null) throw UnprocessableException("tables.area_not_found")
        if (tables.existsByLabelIgnoreCaseAndIdNot(label, NO_ID)) throw ConflictException("tables.label_taken", label)
        return tables.save(DiningTableEntity(req.areaId, label, req.seats, req.sortOrder ?: 0, req.active ?: true)).toAdmin()
    }

    @Transactional
    fun updateTable(id: UUID, req: DiningTableRequest): DiningTableAdmin {
        val table = tables.findByIdOrNull(id) ?: throw NotFoundException("Table")
        val label = req.label.trim()
        if (areas.findByIdOrNull(req.areaId) == null) throw UnprocessableException("tables.area_not_found")
        if (tables.existsByLabelIgnoreCaseAndIdNot(label, id)) throw ConflictException("tables.label_taken", label)
        val active = req.active ?: table.active
        if (!active && sessions.findByTableIdAndStatus(id, SessionStatus.OPEN) != null) throw ConflictException("tables.table_in_use", table.label)
        table.areaId = req.areaId
        table.label = label
        table.seats = req.seats
        table.sortOrder = req.sortOrder ?: table.sortOrder
        table.active = active
        return table.toAdmin()
    }

    /** A photo of the QR leaked (or a sticker was replaced): the old code stops working at once. */
    @Transactional
    fun regenerateQr(id: UUID): DiningTableAdmin {
        val table = tables.findByIdOrNull(id) ?: throw NotFoundException("Table")
        table.qrCode = newQrCode()
        return table.toAdmin()
    }

    // ---------------------------------------------------------------- floor
    fun floor(locale: Locale): List<FloorArea> {
        val open = sessions.findAllByStatus(SessionStatus.OPEN)
        val ids = open.map { it.id }
        val billsById = bills.of(ids)
        val guestsBySession = guests.findAllBySessionIdInOrderByRequestedAtAsc(ids).groupBy { it.sessionId }
        val pendingBySession = pendingRounds.findAllBySessionIdInOrderByCreatedAtAsc(ids).groupBy { it.sessionId }
        val byTable = open.associateBy { it.tableId }
        val activeTables = tables.findAllByOrderBySortOrderAscLabelAsc().filter { it.active }.groupBy { it.areaId }
        return areas.findAllByOrderBySortOrderAsc().filter { it.active }.map { area ->
            FloorArea(
                id = area.id,
                name = LocalizedText.fromMap(area.name)!!.resolve(locale),
                tables = activeTables[area.id].orEmpty().map { table ->
                    val s = byTable[table.id]
                    FloorTable(table.id, table.label, table.seats, s?.let { views.floor(it, billsById.getValue(it.id), guestsBySession[it.id].orEmpty(), pendingBySession[it.id].orEmpty()) })
                },
            )
        }
    }

    // ---------------------------------------------------------------- sessions (waiter)
    fun get(sessionId: UUID, locale: Locale): TableSession = views.staff(load(sessionId), locale)

    @Transactional
    fun open(tableId: UUID, guestCount: Int, locale: Locale): TableSession {
        if (!settings.features().tables) throw UnprocessableException("tables.disabled")
        val table = tables.findByIdOrNull(tableId)?.takeIf { it.active } ?: throw NotFoundException("Table")
        if (sessions.findByTableIdAndStatus(tableId, SessionStatus.OPEN) != null) throw ConflictException("tables.table_occupied", table.label)
        val actor = Actor.current()
        val session = try {
            sessions.saveAndFlush(TableSessionEntity(tableId, guestCount, actor?.userId, actor?.name, clock.instant()))
        } catch (e: DataIntegrityViolationException) {
            throw ConflictException("tables.table_occupied", table.label) // another waiter (or a guest) took it a moment ago
        }
        changed(session)
        logger.info("Table {} opened for {} guest(s)", table.label, guestCount)
        return views.staff(session, locale)
    }

    /** A waiter adding a round also confirms a table started from the QR code. */
    @Transactional
    fun addRound(sessionId: UUID, req: AddRoundRequest, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        val waiter = Actor.current()?.name
        if (session.awaitingConfirmation) confirmSession(session, waiter)
        val orderId = orders.placeRound(
            sessionId = session.id,
            tableLabel = labelOf(session),
            lines = req.lines.map { RoundLine(it.productId, it.quantity, it.modifierOptionIds.orEmpty(), it.notes) },
            notes = req.notes,
            locale = locale,
        )
        val placed = orders.sessionOrders(listOf(session.id)).first { it.orderId == orderId }
        log.ordered(session.id, waiter, ActorKind.WAITER, placed.lines.map { LoggedItem(it.productName.toMap(), it.quantity) })
        changed(session)
        return views.staff(session, locale)
    }

    /**
     * Takes a cash or card payment: an amount (the whole bill or an equal share) or selected units. Units reserved by a
     * guest's online payment in progress cannot be taken. The table is freed once nothing is left to pay.
     */
    @Transactional
    fun pay(sessionId: UUID, req: TablePaymentRequest, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        val bill = bills.of(session.id)
        if (req.items != null && req.amount != null) throw UnprocessableException("tables.payment_amount_or_items")

        val allocations = bill.allocate(req.items.orEmpty().groupBy { it.lineId }.mapValues { (_, items) -> items.sumOf { it.quantity } }, locale)
        val amount = if (req.items != null) {
            allocations.fold(BigDecimal.ZERO) { acc, a -> acc + a.amount }.setScale(2)
        } else {
            req.amount?.let(Money::of) ?: throw UnprocessableException("tables.payment_amount_required")
        }
        if (amount.signum() <= 0) throw UnprocessableException("tables.payment_amount_required")
        if (amount > bill.payable) throw UnprocessableException("tables.payment_exceeds_bill", bill.payable.toPlainString())

        val method = InPersonMethod.valueOf(req.method.value)
        val tendered = req.tendered?.let(Money::of)
        if (tendered != null && method != InPersonMethod.CASH) throw UnprocessableException("tables.tendered_cash_only")
        if (tendered != null && tendered < amount) throw UnprocessableException("tables.tendered_too_low", amount.toPlainString())

        payments.record(session.id, method, amount, settings.currency(), tendered, allocations)
        log.paid(session.id, Actor.current()?.name, ActorKind.WAITER, method.name, amount, settings.currency(), items(bill, allocations.associate { it.orderLineId to it.quantity }))
        if ((bill.remaining - amount).signum() == 0) close(session) else changed(session)
        return views.staff(session, locale)
    }

    @Transactional
    fun void(sessionId: UUID, paymentId: UUID, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        if (!payments.void(session.id, paymentId)) throw NotFoundException("Payment")
        changed(session)
        return views.staff(session, locale)
    }

    /** Frees the table when nothing is owed, e.g. guests who left without ordering, or a bill paid in full. */
    @Transactional
    fun close(sessionId: UUID, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        val bill = bills.of(session.id)
        if (bill.remaining.signum() > 0) throw ConflictException("tables.bill_not_paid", bill.remaining.toPlainString())
        if (bill.pending.signum() > 0) throw ConflictException("tables.payment_in_progress")
        close(session)
        return views.staff(session, locale)
    }

    @Transactional
    fun transfer(sessionId: UUID, tableId: UUID, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        if (session.tableId == tableId) return views.staff(session, locale)
        val target = tables.findByIdOrNull(tableId)?.takeIf { it.active } ?: throw NotFoundException("Table")
        if (sessions.findByTableIdAndStatus(tableId, SessionStatus.OPEN) != null) throw ConflictException("tables.table_occupied", target.label)
        val from = session.tableId
        session.tableId = tableId
        sessions.flush()
        events.publishEvent(TableChanged(session.id, from, TableChanged.Kind.UPDATED))
        changed(session)
        return views.staff(session, locale)
    }

    /**
     * Joins this table's bill into another open table, with its guests, their pending rounds and the log. Only before
     * anything was paid, so no payment changes table.
     */
    @Transactional
    fun merge(sessionId: UUID, targetSessionId: UUID, locale: Locale): TableSession {
        if (sessionId == targetSessionId) throw UnprocessableException("tables.merge_same")
        val source = loadOpen(sessionId)
        val target = loadOpen(targetSessionId)
        if (payments.forSessions(listOf(source.id)).any { it.state.name in setOf("CAPTURED", "PENDING") }) throw ConflictException("tables.merge_has_payments")
        orders.moveToSession(source.id, target.id)
        guests.findAllBySessionIdInOrderByRequestedAtAsc(listOf(source.id)).forEach {
            it.sessionId = target.id
            if (it.role == GuestRole.OWNER) it.role = GuestRole.MEMBER // the target table keeps its owner
        }
        pendingRounds.findAllBySessionIdInOrderByCreatedAtAsc(listOf(source.id)).forEach { it.sessionId = target.id }
        activity.findAllBySessionId(source.id).forEach { it.sessionId = target.id }
        if (target.awaitingConfirmation) confirmSession(target, Actor.current()?.name)
        target.guests += source.guests
        source.status = SessionStatus.MERGED
        source.mergedIntoId = target.id
        source.closedAt = clock.instant()
        events.publishEvent(TableChanged(source.id, source.tableId, TableChanged.Kind.CLOSED))
        changed(target)
        return views.staff(target, locale)
    }

    fun preBill(sessionId: UUID) {
        val session = load(sessionId)
        val bill = bills.of(session.id)
        val ro = Locale.of("ro")
        val lines = bill.active.flatMap { it.lines }
            .groupBy { (listOf(it.productName.resolve(ro)) + it.modifiers.map { m -> m.resolve(ro) }).joinToString(" + ") }
            .map { (name, lines) -> PreBillLine(name, lines.sumOf { it.quantity }, lines.fold(BigDecimal.ZERO) { acc, l -> acc + l.total }) }
        events.publishEvent(
            PreBillRequested(
                tableLabel = labelOf(session),
                waiterName = session.waiterName,
                lines = lines,
                total = bill.total,
                paid = bill.paid,
                remaining = bill.remaining,
                currency = settings.currency(),
                at = clock.instant(),
            ),
        )
    }

    // ---------------------------------------------------------------- QR guests (waiter side)
    /** Confirms a table started by guests; [locale] null when called internally. */
    @Transactional
    fun confirm(sessionId: UUID, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        if (!session.awaitingConfirmation) return views.staff(session, locale)
        confirmSession(session, Actor.current()?.name)
        changed(session)
        return views.staff(session, locale)
    }

    /** Nobody is sitting at a table started from the QR code (or it is a prank): the table is freed. */
    @Transactional
    fun reject(sessionId: UUID, reason: String?, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        if (!session.awaitingConfirmation) throw ConflictException("tables.already_confirmed")
        guests.findAllBySessionIdInOrderByRequestedAtAsc(listOf(session.id)).forEach { it.status = GuestStatus.REMOVED; it.decidedAt = clock.instant() }
        pendingRounds.findAllBySessionIdInOrderByCreatedAtAsc(listOf(session.id)).filter { it.status == PendingRoundStatus.PENDING }.forEach {
            decide(it, PendingRoundStatus.REJECTED, Actor.current()?.name, reason)
        }
        session.status = SessionStatus.CLOSED
        session.closedAt = clock.instant()
        events.publishEvent(TableChanged(session.id, session.tableId, TableChanged.Kind.CLOSED))
        logger.info("Guest-started session {} refused", session.id)
        return views.staff(session, locale)
    }

    @Transactional
    fun confirmRound(sessionId: UUID, roundId: UUID, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        val round = pendingRounds.findByIdOrNull(roundId)?.takeIf { it.sessionId == session.id } ?: throw NotFoundException("Round")
        confirmRound(session, round, Actor.current()?.name)
        changed(session)
        return views.staff(session, locale)
    }

    @Transactional
    fun rejectRound(sessionId: UUID, roundId: UUID, reason: String?, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        val round = pendingRounds.findByIdOrNull(roundId)?.takeIf { it.sessionId == session.id } ?: throw NotFoundException("Round")
        if (round.status != PendingRoundStatus.PENDING) throw ConflictException("tables.round_decided")
        decide(round, PendingRoundStatus.REJECTED, Actor.current()?.name, reason?.trim()?.ifBlank { null })
        log.roundRejected(session.id, Actor.current()?.name, round.lines.map { LoggedItem(it.name, it.quantity) }, round.reason)
        changed(session)
        return views.staff(session, locale)
    }

    /** approve / reject a guest asking to join; remove a guest already at the table. */
    @Transactional
    fun decideGuest(sessionId: UUID, guestId: UUID, action: String, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        val guest = guests.findByIdOrNull(guestId)?.takeIf { it.sessionId == session.id } ?: throw NotFoundException("Guest")
        val waiter = Actor.current()?.name
        when (action) {
            "approve", "reject" -> decideJoin(session, guest, approve = action == "approve")
            "remove" -> {
                if (guest.status !in setOf(GuestStatus.ACTIVE, GuestStatus.PENDING)) throw ConflictException("tables.guest_decided")
                val wasOwner = guest.role == GuestRole.OWNER && guest.status == GuestStatus.ACTIVE
                guest.status = GuestStatus.REMOVED
                guest.decidedAt = clock.instant()
                if (wasOwner) {
                    // The oldest guest still at the table takes over as owner.
                    guests.findAllBySessionIdInOrderByRequestedAtAsc(listOf(session.id)).firstOrNull { it.status == GuestStatus.ACTIVE }?.role = GuestRole.OWNER
                }
                log.removed(session.id, guest.name, waiter)
            }
            else -> throw UnprocessableException("tables.unknown_action")
        }
        changed(session)
        return views.staff(session, locale)
    }

    /** Shared by the owner (guest app) and the waiter. */
    fun decideJoin(session: TableSessionEntity, guest: TableGuestEntity, approve: Boolean) {
        if (guest.status != GuestStatus.PENDING) throw ConflictException("tables.guest_decided")
        guest.decidedAt = clock.instant()
        if (approve) {
            guest.status = GuestStatus.ACTIVE
            val present = guests.findAllBySessionIdInOrderByRequestedAtAsc(listOf(session.id)).count { it.status == GuestStatus.ACTIVE }
            session.guests = maxOf(session.guests, present)
            log.joined(session.id, guest.name)
        } else {
            guest.status = GuestStatus.REJECTED
        }
    }

    // ---------------------------------------------------------------- automatic confirmation
    /**
     * Guest rounds and guest-started tables nobody answered within the configured time are confirmed automatically, so
     * guests are never left waiting. Join requests expire. Each item runs in its own transaction: one round that cannot
     * be placed any more (a product sold out meanwhile) is refused without blocking the others.
     */
    @Scheduled(fixedDelayString = "PT10S", initialDelayString = "PT20S")
    fun confirmOverdue() {
        val now = clock.instant()
        sessions.findAllByStatusAndConfirmedAtIsNullAndConfirmDeadlineBefore(SessionStatus.OPEN, now).forEach { s ->
            eachInOwnTransaction.executeWithoutResult {
                val session = sessions.findByIdOrNull(s.id)!!
                if (session.awaitingConfirmation && session.status == SessionStatus.OPEN) {
                    confirmSession(session, null)
                    changed(session)
                }
            }
        }
        pendingRounds.findAllByStatusAndDeadlineBefore(PendingRoundStatus.PENDING, now).forEach { r ->
            try {
                eachInOwnTransaction.executeWithoutResult {
                    val round = pendingRounds.findByIdOrNull(r.id)!!
                    val session = sessions.findByIdOrNull(round.sessionId)!!
                    if (round.status == PendingRoundStatus.PENDING && session.status == SessionStatus.OPEN) {
                        confirmRound(session, round, null)
                        changed(session)
                    }
                }
            } catch (e: Exception) {
                logger.warn("Guest round {} could not be confirmed automatically: {}", r.id, e.message)
                eachInOwnTransaction.executeWithoutResult {
                    val round = pendingRounds.findByIdOrNull(r.id)!!
                    if (round.status == PendingRoundStatus.PENDING) {
                        decide(round, PendingRoundStatus.REJECTED, null, e.message)
                        log.roundRejected(round.sessionId, null, round.lines.map { LoggedItem(it.name, it.quantity) }, e.message)
                        changed(sessions.findByIdOrNull(round.sessionId)!!)
                    }
                }
            }
        }
        guests.findAllByStatusAndRequestedAtBefore(GuestStatus.PENDING, now.minus(JOIN_REQUEST_TTL)).forEach { g ->
            eachInOwnTransaction.executeWithoutResult {
                val guest = guests.findByIdOrNull(g.id)!!
                if (guest.status == GuestStatus.PENDING) {
                    guest.status = GuestStatus.REJECTED
                    guest.decidedAt = now
                    changed(sessions.findByIdOrNull(guest.sessionId)!!)
                }
            }
        }
    }

    // ---------------------------------------------------------------- online payments from guests
    /** A guest's payment went through: log it, and free the table when nothing is left; refund a payment that came late. */
    @ApplicationModuleListener
    fun on(event: SessionPaymentCaptured) {
        val session = sessions.findByIdOrNull(event.sessionId) ?: return
        val bill = bills.of(session.id)
        if (session.status != SessionStatus.OPEN || bill.paid > bill.total) {
            payments.refund(event.paymentId)
            logger.warn("Late online payment {} for session {} refunded", event.paymentId, session.id)
            changed(session)
            return
        }
        val payer = guestPayments.findByIdOrNull(event.paymentId)?.let { guests.findByIdOrNull(it.guestId)?.name }
        val allocations = bill.payments.first { it.id == event.paymentId }.allocations
        log.paid(session.id, payer, ActorKind.GUEST, "ONLINE", event.amount, event.currency, items(bill, allocations.associate { it.orderLineId to it.quantity }))
        if (bill.remaining.signum() == 0 && bill.pending.signum() == 0) close(session) else changed(session)
    }

    @ApplicationModuleListener
    fun on(event: SessionPaymentEnded) {
        sessions.findByIdOrNull(event.sessionId)?.let(::changed)
    }

    // ---------------------------------------------------------------- internals
    fun confirmSession(session: TableSessionEntity, by: String?) {
        session.confirmedAt = clock.instant()
        if (by != null && session.waiterName == null) {
            session.waiterId = Actor.current()?.userId
            session.waiterName = by
        }
        log.tableConfirmed(session.id, by)
    }

    /** Sends a guest round to the kitchen and bar; [by] is the waiter, null when confirmed automatically. */
    private fun confirmRound(session: TableSessionEntity, round: PendingRoundEntity, by: String?) {
        if (round.status != PendingRoundStatus.PENDING) throw ConflictException("tables.round_decided")
        if (session.awaitingConfirmation) confirmSession(session, by)
        round.orderId = orders.placeRound(
            sessionId = session.id,
            tableLabel = labelOf(session),
            lines = round.lines.map { RoundLine(it.productId, it.quantity, it.optionIds, it.notes) },
            notes = round.notes,
            locale = Locale.of("ro"),
        )
        decide(round, PendingRoundStatus.CONFIRMED, by, null)
        log.roundConfirmed(session.id, by, round.lines.map { LoggedItem(it.name, it.quantity) })
    }

    private fun decide(round: PendingRoundEntity, status: PendingRoundStatus, by: String?, reason: String?) {
        round.status = status
        round.decidedAt = clock.instant()
        round.decidedBy = by ?: "auto"
        round.reason = reason
    }

    private fun close(session: TableSessionEntity) {
        orders.settle(session.id)
        session.status = SessionStatus.CLOSED
        session.closedAt = clock.instant()
        events.publishEvent(TableChanged(session.id, session.tableId, TableChanged.Kind.CLOSED))
        logger.info("Table session {} closed", session.id)
    }

    fun changed(session: TableSessionEntity, kind: TableChanged.Kind = TableChanged.Kind.UPDATED) =
        events.publishEvent(TableChanged(session.id, session.tableId, kind))

    private fun items(bill: Bill, units: Map<UUID, Int>) =
        units.mapNotNull { (lineId, qty) -> bill.line(lineId)?.let { LoggedItem(it.productName.toMap(), qty) } }

    fun labelOf(session: TableSessionEntity) = tables.findByIdOrNull(session.tableId)?.label ?: "?"

    private fun load(id: UUID) = sessions.findByIdOrNull(id) ?: throw NotFoundException("Table session")

    private fun loadOpen(id: UUID) = load(id).also { if (it.status != SessionStatus.OPEN) throw ConflictException("tables.session_closed") }

    private fun FloorAreaEntity.toAdmin() = FloorAreaAdmin(id, LocalizedText.fromMap(name)!!.toDto(), sortOrder, active)

    private fun DiningTableEntity.toAdmin() = DiningTableAdmin(id, areaId, label, seats, sortOrder, active, qrCode)

    private companion object {
        val NO_ID: UUID = UUID(0, 0)
        val JOIN_REQUEST_TTL: Duration = Duration.ofMinutes(15)
    }
}
