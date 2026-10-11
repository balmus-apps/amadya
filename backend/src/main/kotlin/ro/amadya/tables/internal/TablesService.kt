package ro.amadya.tables.internal

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.contract.model.AddRoundRequest
import ro.amadya.contract.model.DiningTableAdmin
import ro.amadya.contract.model.DiningTableRequest
import ro.amadya.contract.model.FloorArea
import ro.amadya.contract.model.FloorAreaAdmin
import ro.amadya.contract.model.FloorAreaRequest
import ro.amadya.contract.model.FloorSession
import ro.amadya.contract.model.FloorTable
import ro.amadya.contract.model.InPersonPaymentMethod
import ro.amadya.contract.model.TablePayment
import ro.amadya.contract.model.TablePaymentRequest
import ro.amadya.contract.model.TableRound
import ro.amadya.contract.model.TableRoundLine
import ro.amadya.contract.model.TableSession
import ro.amadya.contract.model.TableSessionStatus
import ro.amadya.ordering.RoundLine
import ro.amadya.ordering.SessionOrder
import ro.amadya.ordering.TableOrders
import ro.amadya.payments.InPersonMethod
import ro.amadya.payments.LineAllocation
import ro.amadya.payments.SessionPayment
import ro.amadya.payments.SessionPayments
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.Actor
import ro.amadya.shared.ConflictException
import ro.amadya.shared.LocalizedText
import ro.amadya.shared.Money
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.UnprocessableException
import ro.amadya.shared.toMoney
import ro.amadya.tables.PreBillLine
import ro.amadya.tables.PreBillRequested
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import ro.amadya.contract.model.OrderStatus as OrderStatusDto

/** Internal event: something about a table session changed; pushed to the waiter apps after commit. */
data class TableChanged(val sessionId: UUID, val tableId: UUID?, val kind: Kind, val orderNumber: String? = null) {
    enum class Kind { UPDATED, ROUND_READY, CLOSED }
}

/** A session's rounds and payments, with the bill derived from them. */
private class Bill(val rounds: List<SessionOrder>, val payments: List<SessionPayment>) {
    private val captured = payments.filter { !it.voided }
    val active = rounds.filter { it.status != "CANCELLED" }
    val total: BigDecimal = active.fold(BigDecimal.ZERO) { acc, r -> acc + r.total }.setScale(2)
    val paid: BigDecimal = captured.fold(BigDecimal.ZERO) { acc, p -> acc + p.amount }.setScale(2)
    val remaining: BigDecimal = (total - paid).max(BigDecimal.ZERO).setScale(2)

    private val allocations = captured.flatMap { it.allocations }.groupBy { it.orderLineId }
    fun paidQuantity(lineId: UUID) = allocations[lineId]?.sumOf { it.quantity } ?: 0
    fun paidAmount(lineId: UUID): BigDecimal = allocations[lineId]?.fold(BigDecimal.ZERO) { acc, a -> acc + a.amount } ?: BigDecimal.ZERO
}

@Service
@Transactional(readOnly = true)
class TablesService(
    private val areas: FloorAreaRepository,
    private val tables: DiningTableRepository,
    private val sessions: TableSessionRepository,
    private val orders: TableOrders,
    private val payments: SessionPayments,
    private val settings: RestaurantSettingsApi,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

    // ---------------------------------------------------------------- floor
    fun floor(locale: Locale): List<FloorArea> {
        val open = sessions.findAllByStatus(SessionStatus.OPEN)
        val bills = bills(open.map { it.id })
        val byTable = open.associateBy { it.tableId }
        val activeTables = tables.findAllByOrderBySortOrderAscLabelAsc().filter { it.active }.groupBy { it.areaId }
        return areas.findAllByOrderBySortOrderAsc().filter { it.active }.map { area ->
            FloorArea(
                id = area.id,
                name = LocalizedText.fromMap(area.name)!!.resolve(locale),
                tables = activeTables[area.id].orEmpty().map { table ->
                    val session = byTable[table.id]
                    FloorTable(table.id, table.label, table.seats, session?.let { floorSession(it, bills.getValue(it.id)) })
                },
            )
        }
    }

    // ---------------------------------------------------------------- sessions
    fun get(sessionId: UUID, locale: Locale): TableSession = view(load(sessionId), locale)

    @Transactional
    fun open(tableId: UUID, guests: Int, locale: Locale): TableSession {
        if (!settings.features().tables) throw UnprocessableException("tables.disabled")
        val table = tables.findByIdOrNull(tableId)?.takeIf { it.active } ?: throw NotFoundException("Table")
        if (sessions.findByTableIdAndStatus(tableId, SessionStatus.OPEN) != null) throw ConflictException("tables.table_occupied", table.label)
        val actor = Actor.current()
        val session = try {
            sessions.saveAndFlush(TableSessionEntity(tableId, guests, actor?.userId, actor?.name, clock.instant()))
        } catch (e: DataIntegrityViolationException) {
            throw ConflictException("tables.table_occupied", table.label) // another waiter seated it a moment ago
        }
        changed(session)
        log.info("Table {} opened for {} guest(s)", table.label, guests)
        return view(session, locale)
    }

    @Transactional
    fun addRound(sessionId: UUID, req: AddRoundRequest, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        val table = tables.findByIdOrNull(session.tableId)!!
        orders.placeRound(
            sessionId = session.id,
            tableLabel = table.label,
            lines = req.lines.map { RoundLine(it.productId, it.quantity, it.modifierOptionIds.orEmpty(), it.notes) },
            notes = req.notes,
            locale = locale,
        )
        changed(session)
        return view(session, locale)
    }

    /**
     * Takes a cash or card payment: an amount (the whole bill or an equal share) or selected units. Units are paid at
     * their share of the line total; the last unit of a line takes whatever rounding left, so a line is never over- or
     * under-charged. The table is freed once nothing is left to pay.
     */
    @Transactional
    fun pay(sessionId: UUID, req: TablePaymentRequest, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        val bill = bill(session.id)
        if (req.items != null && req.amount != null) throw UnprocessableException("tables.payment_amount_or_items")

        val allocations = req.items.orEmpty().groupBy { it.lineId }.map { (lineId, items) ->
            val line = bill.active.flatMap { it.lines }.firstOrNull { it.lineId == lineId } ?: throw UnprocessableException("tables.item_not_found")
            val quantity = items.sumOf { it.quantity }
            val unpaid = line.quantity - bill.paidQuantity(lineId)
            if (quantity > unpaid) throw UnprocessableException("tables.item_already_paid", line.productName.resolve(locale))
            val amount = if (quantity == unpaid) {
                line.total - bill.paidAmount(lineId)
            } else {
                line.total.multiply(BigDecimal(quantity)).divide(BigDecimal(line.quantity), 2, RoundingMode.HALF_UP)
            }
            LineAllocation(lineId, quantity, amount.setScale(2))
        }
        val amount = if (req.items != null) {
            allocations.fold(BigDecimal.ZERO) { acc, a -> acc + a.amount }.setScale(2)
        } else {
            req.amount?.let(Money::of) ?: throw UnprocessableException("tables.payment_amount_required")
        }
        if (amount.signum() <= 0) throw UnprocessableException("tables.payment_amount_required")
        if (amount > bill.remaining) throw UnprocessableException("tables.payment_exceeds_bill", bill.remaining.toPlainString())

        val method = InPersonMethod.valueOf(req.method.value)
        val tendered = req.tendered?.let(Money::of)
        if (tendered != null && method != InPersonMethod.CASH) throw UnprocessableException("tables.tendered_cash_only")
        if (tendered != null && tendered < amount) throw UnprocessableException("tables.tendered_too_low", amount.toPlainString())

        payments.record(session.id, method, amount, settings.currency(), tendered, allocations)
        if ((bill.remaining - amount).signum() == 0) close(session) else changed(session)
        return view(session, locale)
    }

    @Transactional
    fun void(sessionId: UUID, paymentId: UUID, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        if (!payments.void(session.id, paymentId)) throw NotFoundException("Payment")
        changed(session)
        return view(session, locale)
    }

    /** Frees the table when nothing is owed, e.g. guests who left without ordering, or a bill paid in full. */
    @Transactional
    fun close(sessionId: UUID, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        val remaining = bill(session.id).remaining
        if (remaining.signum() > 0) throw ConflictException("tables.bill_not_paid", remaining.toPlainString())
        close(session)
        return view(session, locale)
    }

    @Transactional
    fun transfer(sessionId: UUID, tableId: UUID, locale: Locale): TableSession {
        val session = loadOpen(sessionId)
        if (session.tableId == tableId) return view(session, locale)
        val target = tables.findByIdOrNull(tableId)?.takeIf { it.active } ?: throw NotFoundException("Table")
        if (sessions.findByTableIdAndStatus(tableId, SessionStatus.OPEN) != null) throw ConflictException("tables.table_occupied", target.label)
        val from = session.tableId
        session.tableId = tableId
        sessions.flush()
        events.publishEvent(TableChanged(session.id, from, TableChanged.Kind.UPDATED))
        changed(session)
        return view(session, locale)
    }

    /** Joins this table's bill into another open table. Only before anything was paid, so no payment changes table. */
    @Transactional
    fun merge(sessionId: UUID, targetSessionId: UUID, locale: Locale): TableSession {
        if (sessionId == targetSessionId) throw UnprocessableException("tables.merge_same")
        val source = loadOpen(sessionId)
        val target = loadOpen(targetSessionId)
        if (payments.forSessions(listOf(source.id)).any { !it.voided }) throw ConflictException("tables.merge_has_payments")
        orders.moveToSession(source.id, target.id)
        target.guests += source.guests
        source.status = SessionStatus.MERGED
        source.mergedIntoId = target.id
        source.closedAt = clock.instant()
        events.publishEvent(TableChanged(source.id, source.tableId, TableChanged.Kind.CLOSED))
        changed(target)
        return view(target, locale)
    }

    fun preBill(sessionId: UUID) {
        val session = load(sessionId)
        val bill = bill(session.id)
        val ro = Locale.of("ro")
        val lines = bill.active.flatMap { it.lines }
            .groupBy { (listOf(it.productName.resolve(ro)) + it.modifiers.map { m -> m.resolve(ro) }).joinToString(" + ") }
            .map { (name, lines) -> PreBillLine(name, lines.sumOf { it.quantity }, lines.fold(BigDecimal.ZERO) { acc, l -> acc + l.total }) }
        events.publishEvent(
            PreBillRequested(
                tableLabel = tables.findByIdOrNull(session.tableId)?.label ?: "?",
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

    // ---------------------------------------------------------------- internals
    private fun close(session: TableSessionEntity) {
        orders.settle(session.id)
        session.status = SessionStatus.CLOSED
        session.closedAt = clock.instant()
        events.publishEvent(TableChanged(session.id, session.tableId, TableChanged.Kind.CLOSED))
        log.info("Table session {} closed", session.id)
    }

    private fun changed(session: TableSessionEntity) = events.publishEvent(TableChanged(session.id, session.tableId, TableChanged.Kind.UPDATED))

    private fun load(id: UUID) = sessions.findByIdOrNull(id) ?: throw NotFoundException("Table session")

    private fun loadOpen(id: UUID) = load(id).also { if (it.status != SessionStatus.OPEN) throw ConflictException("tables.session_closed") }

    private fun bill(sessionId: UUID) = bills(listOf(sessionId)).getValue(sessionId)

    private fun bills(sessionIds: List<UUID>): Map<UUID, Bill> {
        val rounds = orders.sessionOrders(sessionIds).groupBy { it.sessionId }
        val paid = payments.forSessions(sessionIds).groupBy { it.sessionId }
        return sessionIds.associateWith { Bill(rounds[it].orEmpty(), paid[it].orEmpty()) }
    }

    private fun floorSession(session: TableSessionEntity, bill: Bill): FloorSession {
        val currency = settings.currency()
        return FloorSession(
            id = session.id,
            guests = session.guests,
            openedAt = session.openedAt.atOffset(ZoneOffset.UTC),
            total = bill.total.toMoney(currency),
            remaining = bill.remaining.toMoney(currency),
            roundsInKitchen = bill.active.count { it.status == "PLACED" || it.status == "PREPARING" },
            roundsReady = bill.active.count { it.status == "READY" },
            waiterName = session.waiterName,
        )
    }

    private fun view(session: TableSessionEntity, locale: Locale): TableSession {
        val bill = bill(session.id)
        val table = tables.findByIdOrNull(session.tableId)!!
        val area = areas.findByIdOrNull(table.areaId)
        val currency = settings.currency()
        return TableSession(
            id = session.id,
            tableId = table.id,
            tableLabel = table.label,
            status = TableSessionStatus.forValue(session.status.name),
            guests = session.guests,
            openedAt = session.openedAt.atOffset(ZoneOffset.UTC),
            rounds = bill.rounds.map { round ->
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
                            notes = l.notes,
                        )
                    },
                    placedAt = round.placedAt?.atOffset(ZoneOffset.UTC),
                    readyAt = round.readyAt?.atOffset(ZoneOffset.UTC),
                )
            },
            payments = bill.payments.map { p ->
                TablePayment(
                    id = p.id,
                    method = InPersonPaymentMethod.forValue(p.method.name),
                    amount = p.amount.toMoney(p.currency),
                    status = if (p.voided) TablePayment.Status.VOIDED else TablePayment.Status.CAPTURED,
                    createdAt = p.createdAt.atOffset(ZoneOffset.UTC),
                    tendered = p.tendered?.toMoney(p.currency),
                    change = p.tendered?.let { (it - p.amount).toMoney(p.currency) },
                    itemCount = p.allocations.takeIf { it.isNotEmpty() }?.sumOf { it.quantity },
                )
            },
            total = bill.total.toMoney(currency),
            paid = bill.paid.toMoney(currency),
            remaining = bill.remaining.toMoney(currency),
            areaName = area?.let { LocalizedText.fromMap(it.name)!!.resolve(locale) },
            waiterName = session.waiterName,
            closedAt = session.closedAt?.atOffset(ZoneOffset.UTC),
            mergedIntoSessionId = session.mergedIntoId,
        )
    }

    private fun FloorAreaEntity.toAdmin() = FloorAreaAdmin(id, LocalizedText.fromMap(name)!!.toDto(), sortOrder, active)

    private fun DiningTableEntity.toAdmin() = DiningTableAdmin(id, areaId, label, seats, sortOrder, active)

    private companion object {
        val NO_ID: UUID = UUID(0, 0)
    }
}
