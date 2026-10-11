package ro.amadya.tables.internal

import org.springframework.stereotype.Component
import ro.amadya.ordering.SessionOrder
import ro.amadya.ordering.SessionOrderLine
import ro.amadya.ordering.TableOrders
import ro.amadya.payments.LineAllocation
import ro.amadya.payments.SessionPayment
import ro.amadya.payments.SessionPaymentState
import ro.amadya.payments.SessionPayments
import ro.amadya.shared.UnprocessableException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.UUID

/**
 * A session's rounds and payments, with the bill derived from them. Online payments still in progress are not paid yet,
 * but their units are reserved and their amount cannot be paid twice.
 */
class Bill(val rounds: List<SessionOrder>, val payments: List<SessionPayment>) {
    private val captured = payments.filter { it.state == SessionPaymentState.CAPTURED }
    private val inProgress = payments.filter { it.state == SessionPaymentState.PENDING }
    val active = rounds.filter { it.status != "CANCELLED" }
    val total: BigDecimal = sum(active.map { it.total })
    val paid: BigDecimal = sum(captured.map { it.amount })
    val remaining: BigDecimal = (total - paid).max(BigDecimal.ZERO).setScale(2)
    val pending: BigDecimal = sum(inProgress.map { it.amount })

    /** What can be paid now: the rest minus online payments still in progress. */
    val payable: BigDecimal = (remaining - pending).max(BigDecimal.ZERO).setScale(2)

    private val paidUnits = captured.flatMap { it.allocations }.groupBy { it.orderLineId }
    private val reservedUnits = inProgress.flatMap { it.allocations }.groupBy { it.orderLineId }

    fun paidQuantity(lineId: UUID) = paidUnits[lineId]?.sumOf { it.quantity } ?: 0
    fun reservedQuantity(lineId: UUID) = reservedUnits[lineId]?.sumOf { it.quantity } ?: 0
    private fun paidAmount(lineId: UUID): BigDecimal = sum(paidUnits[lineId].orEmpty().map { it.amount })

    fun line(lineId: UUID): SessionOrderLine? = active.flatMap { it.lines }.firstOrNull { it.lineId == lineId }

    /**
     * Prices selected units. Units are charged their share of the line total; taking every unit still unpaid (none
     * reserved) charges whatever is left of the line, so a line is never over- or under-charged.
     */
    fun allocate(items: Map<UUID, Int>, locale: Locale): List<LineAllocation> = items.map { (lineId, quantity) ->
        val line = line(lineId) ?: throw UnprocessableException("tables.item_not_found")
        val unpaid = line.quantity - paidQuantity(lineId)
        val free = unpaid - reservedQuantity(lineId)
        if (quantity > free) throw UnprocessableException("tables.item_already_paid", line.productName.resolve(locale))
        val amount = if (quantity == unpaid) {
            line.total - paidAmount(lineId)
        } else {
            line.total.multiply(BigDecimal(quantity)).divide(BigDecimal(line.quantity), 2, RoundingMode.HALF_UP)
        }
        LineAllocation(lineId, quantity, amount.setScale(2))
    }

    private fun sum(values: List<BigDecimal>) = values.fold(BigDecimal.ZERO) { acc, v -> acc + v }.setScale(2)
}

@Component
class BillReader(private val orders: TableOrders, private val payments: SessionPayments) {
    fun of(sessionId: UUID): Bill = of(listOf(sessionId)).getValue(sessionId)

    fun of(sessionIds: Collection<UUID>): Map<UUID, Bill> {
        val rounds = orders.sessionOrders(sessionIds).groupBy { it.sessionId }
        val paid = payments.forSessions(sessionIds).groupBy { it.sessionId }
        return sessionIds.associateWith { Bill(rounds[it].orEmpty(), paid[it].orEmpty()) }
    }
}
