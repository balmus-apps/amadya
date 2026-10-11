package ro.amadya.tables.internal

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import ro.amadya.contract.model.FloorSession
import ro.amadya.contract.model.GuestView
import ro.amadya.contract.model.PendingRound
import ro.amadya.contract.model.PendingRoundLinesInner
import ro.amadya.contract.model.TableActivity
import ro.amadya.contract.model.TableActivityItemsInner
import ro.amadya.contract.model.TableGuest
import ro.amadya.contract.model.TablePayment
import ro.amadya.contract.model.TableRound
import ro.amadya.contract.model.TableRoundLine
import ro.amadya.contract.model.TableSession
import ro.amadya.contract.model.TableSessionStatus
import ro.amadya.payments.SessionPaymentState
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.LocalizedText
import ro.amadya.shared.toMoney
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import ro.amadya.contract.model.OrderStatus as OrderStatusDto

/** Writes the table's activity log: who joined, ordered and paid. */
@Component
class TableLog(private val repo: TableActivityRepository, private val clock: Clock) {
    fun joined(sessionId: UUID, guest: String) = add(sessionId, ActivityKind.JOINED, guest, ActorKind.GUEST)

    fun removed(sessionId: UUID, guest: String, by: String?) = add(sessionId, ActivityKind.REMOVED, guest, ActorKind.GUEST, mapOf("reason" to by))

    fun ordered(sessionId: UUID, actor: String?, kind: ActorKind, items: List<LoggedItem>) =
        add(sessionId, ActivityKind.ORDERED, actor, kind, mapOf("items" to items.map(LoggedItem::toJson)))

    /** [by] is the waiter who confirmed; null when it was confirmed automatically. */
    fun roundConfirmed(sessionId: UUID, by: String?, items: List<LoggedItem>) =
        add(sessionId, ActivityKind.ROUND_CONFIRMED, by, actorOf(by), mapOf("items" to items.map(LoggedItem::toJson), "automatic" to (by == null)))

    fun roundRejected(sessionId: UUID, by: String?, items: List<LoggedItem>, reason: String?) =
        add(sessionId, ActivityKind.ROUND_REJECTED, by, actorOf(by), mapOf("items" to items.map(LoggedItem::toJson), "reason" to reason))

    fun tableConfirmed(sessionId: UUID, by: String?) =
        add(sessionId, ActivityKind.TABLE_CONFIRMED, by, actorOf(by), mapOf("automatic" to (by == null)))

    fun paid(sessionId: UUID, actor: String?, kind: ActorKind, method: String, amount: BigDecimal, currency: String, items: List<LoggedItem>) =
        add(
            sessionId, ActivityKind.PAID, actor, kind,
            mapOf("method" to method, "amount" to amount.toPlainString(), "currency" to currency, "items" to items.map(LoggedItem::toJson)),
        )

    private fun actorOf(by: String?) = if (by == null) ActorKind.SYSTEM else ActorKind.WAITER

    private fun add(sessionId: UUID, kind: ActivityKind, actor: String?, actorKind: ActorKind, details: Map<String, Any?> = emptyMap()) {
        repo.save(TableActivityEntity(sessionId, clock.instant(), kind, actor, actorKind, details.filterValues { it != null }))
    }
}

data class LoggedItem(val name: Map<String, String>, val quantity: Int) {
    fun toJson() = mapOf("name" to name, "quantity" to quantity)
}

/** Builds what the waiter and the guests see of a table session. */
@Component
class TableViews(
    private val areas: FloorAreaRepository,
    private val tables: DiningTableRepository,
    private val guests: TableGuestRepository,
    private val pendingRounds: PendingRoundRepository,
    private val activity: TableActivityRepository,
    private val bills: BillReader,
    private val settings: RestaurantSettingsApi,
    private val clock: Clock,
) {
    fun staff(session: TableSessionEntity, locale: Locale): TableSession {
        val bill = bills.of(session.id)
        val table = tables.findByIdOrNull(session.tableId)!!
        val currency = settings.currency()
        val sessionGuests = guests.findAllBySessionIdInOrderByRequestedAtAsc(listOf(session.id))
        val pending = pendingRounds.findAllBySessionIdInOrderByCreatedAtAsc(listOf(session.id))
        val guestNames = sessionGuests.associate { it.id to it.name }
        val placedBy = pending.filter { it.orderId != null }.associate { it.orderId!! to guestNames[it.guestId] }
        return TableSession(
            id = session.id,
            tableId = table.id,
            tableLabel = table.label,
            status = TableSessionStatus.forValue(session.status.name),
            guests = session.guests,
            openedAt = session.openedAt.utc(),
            origin = TableSession.Origin.forValue(session.origin.name),
            awaitingConfirmation = session.awaitingConfirmation,
            rounds = rounds(bill, locale) { placedBy[it] },
            payments = bill.payments.filter { it.state == SessionPaymentState.CAPTURED || it.state == SessionPaymentState.VOIDED }.map { p ->
                TablePayment(
                    id = p.id,
                    method = TablePayment.Method.forValue(p.method),
                    amount = p.amount.toMoney(p.currency),
                    status = if (p.voided) TablePayment.Status.VOIDED else TablePayment.Status.CAPTURED,
                    createdAt = p.createdAt.utc(),
                    tendered = p.tendered?.toMoney(p.currency),
                    change = p.tendered?.let { (it - p.amount).toMoney(p.currency) },
                    itemCount = p.allocations.takeIf { it.isNotEmpty() }?.sumOf { it.quantity },
                )
            },
            total = bill.total.toMoney(currency),
            paid = bill.paid.toMoney(currency),
            remaining = bill.remaining.toMoney(currency),
            pending = bill.pending.toMoney(currency),
            guestList = sessionGuests.filter { it.status != GuestStatus.REJECTED }.map(::guest),
            pendingRounds = pending.filter { it.status == PendingRoundStatus.PENDING }.map { pendingRound(it, locale, guestNames[it.guestId], currency) },
            activity = activity(session.id, locale, currency),
            areaName = areas.findByIdOrNull(table.areaId)?.let { LocalizedText.fromMap(it.name)!!.resolve(locale) },
            waiterName = session.waiterName,
            closedAt = session.closedAt?.utc(),
            mergedIntoSessionId = session.mergedIntoId,
            confirmDeadline = session.confirmDeadline?.takeIf { session.awaitingConfirmation }?.utc(),
        )
    }

    /** The guest's view: the bill without names, their own pending rounds, the log; join requests for the owner. */
    fun guest(session: TableSessionEntity, me: TableGuestEntity, locale: Locale): GuestView {
        val currency = settings.currency()
        val table = tables.findByIdOrNull(session.tableId)!!
        val status = when {
            session.status != SessionStatus.OPEN -> GuestView.Status.CLOSED
            session.awaitingConfirmation -> GuestView.Status.AWAITING_CONFIRMATION
            else -> GuestView.Status.OPEN
        }
        val zero = BigDecimal.ZERO.toMoney(currency)
        if (me.status != GuestStatus.ACTIVE) {
            // Waiting to be let in: nothing of the table yet.
            return GuestView(guest(me), status, table.label, settings.guestConfirmMinutes(), emptyList(), emptyList(), emptyList(), emptyList(), zero, zero, zero, zero, zero)
        }
        val bill = bills.of(session.id)
        val sessionGuests = guests.findAllBySessionIdInOrderByRequestedAtAsc(listOf(session.id))
        return GuestView(
            me = guest(me),
            status = status,
            tableLabel = table.label,
            confirmMinutes = settings.guestConfirmMinutes(),
            rounds = rounds(bill, locale) { null },
            pendingRounds = pendingRounds.findAllBySessionIdInOrderByCreatedAtAsc(listOf(session.id))
                .filter { it.status != PendingRoundStatus.CONFIRMED && (it.status == PendingRoundStatus.PENDING || it.guestId == me.id) }
                .filter { it.status == PendingRoundStatus.PENDING || it.decidedAt?.isAfter(clock.instant().minusSeconds(600)) == true }
                .map { pendingRound(it, locale, null, currency) },
            joinRequests = if (me.role == GuestRole.OWNER) sessionGuests.filter { it.status == GuestStatus.PENDING }.map(::guest) else emptyList(),
            activity = activity(session.id, locale, currency),
            total = bill.total.toMoney(currency),
            paid = bill.paid.toMoney(currency),
            remaining = bill.remaining.toMoney(currency),
            pending = bill.pending.toMoney(currency),
            payable = bill.payable.toMoney(currency),
            areaName = areas.findByIdOrNull(table.areaId)?.let { LocalizedText.fromMap(it.name)!!.resolve(locale) },
            confirmDeadline = session.confirmDeadline?.takeIf { session.awaitingConfirmation }?.utc(),
        )
    }

    fun floor(session: TableSessionEntity, bill: Bill, sessionGuests: List<TableGuestEntity>, pending: List<PendingRoundEntity>): FloorSession {
        val currency = settings.currency()
        return FloorSession(
            id = session.id,
            guests = session.guests,
            openedAt = session.openedAt.utc(),
            total = bill.total.toMoney(currency),
            remaining = bill.remaining.toMoney(currency),
            roundsInKitchen = bill.active.count { it.status == "PLACED" || it.status == "PREPARING" },
            roundsReady = bill.active.count { it.status == "READY" },
            awaitingConfirmation = session.awaitingConfirmation,
            pendingRounds = pending.count { it.status == PendingRoundStatus.PENDING },
            joinRequests = sessionGuests.count { it.status == GuestStatus.PENDING },
            waiterName = session.waiterName,
        )
    }

    fun guest(g: TableGuestEntity) =
        TableGuest(g.id, g.name, TableGuest.Role.forValue(g.role.name), TableGuest.Status.forValue(g.status.name), g.requestedAt.utc())

    private fun rounds(bill: Bill, locale: Locale, placedBy: (UUID) -> String?) = bill.rounds.map { round ->
        TableRound(
            orderId = round.orderId,
            number = round.number,
            status = OrderStatusDto.forValue(round.status),
            total = round.total.toMoney(round.currency),
            lines = round.lines.map { l ->
                TableRoundLine(
                    lineId = l.lineId,
                    productName = l.productName.resolve(locale),
                    modifiers = l.modifiers.map { it.resolve(locale) },
                    quantity = l.quantity,
                    unitPrice = l.unitPrice.toMoney(round.currency),
                    total = l.total.toMoney(round.currency),
                    paidQuantity = bill.paidQuantity(l.lineId),
                    reservedQuantity = bill.reservedQuantity(l.lineId),
                    notes = l.notes,
                )
            },
            placedAt = round.placedAt?.utc(),
            readyAt = round.readyAt?.utc(),
            placedBy = placedBy(round.orderId),
        )
    }

    private fun pendingRound(r: PendingRoundEntity, locale: Locale, guestName: String?, currency: String) = PendingRound(
        id = r.id,
        status = PendingRound.Status.forValue(r.status.name),
        lines = r.lines.map { l ->
            PendingRoundLinesInner(
                productName = LocalizedText.fromMap(l.name)!!.resolve(locale),
                modifiers = l.modifiers.map { LocalizedText.fromMap(it)!!.resolve(locale) },
                quantity = l.quantity,
                total = l.total.toMoney(currency),
                notes = l.notes,
            )
        },
        total = r.total.toMoney(currency),
        createdAt = r.createdAt.utc(),
        deadline = r.deadline.utc(),
        guestName = guestName,
        notes = r.notes,
        reason = r.reason,
    )

    @Suppress("UNCHECKED_CAST")
    private fun activity(sessionId: UUID, locale: Locale, currency: String) = activity.findAllBySessionIdInOrderByAtAsc(listOf(sessionId)).map { a ->
        val d = a.details
        TableActivity(
            at = a.at.utc(),
            kind = TableActivity.Kind.forValue(a.kind.name),
            actorKind = TableActivity.ActorKind.forValue(a.actorKind.name),
            actor = a.actor,
            items = (d["items"] as List<Map<String, Any?>>?)?.map {
                TableActivityItemsInner(LocalizedText.fromMap(it["name"] as Map<String, String>)!!.resolve(locale), (it["quantity"] as Number).toInt())
            },
            amount = (d["amount"] as String?)?.let { BigDecimal(it).toMoney(d["currency"] as String? ?: currency) },
            method = (d["method"] as String?)?.let { TableActivity.Method.forValue(it) },
            automatic = d["automatic"] as Boolean?,
            reason = d["reason"] as String?,
        )
    }

    private fun Instant.utc() = atOffset(ZoneOffset.UTC)
}
