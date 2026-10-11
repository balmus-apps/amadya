package ro.amadya.ordering

import ro.amadya.shared.LocalizedText
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale
import java.util.UUID

// ---------------------------------------------------------------- events published by the ordering module

/** An order entered the kitchen flow (paid takeaway, or a counter / dine-in order sent by staff). */
data class OrderPlaced(
    val orderId: UUID,
    val number: String,
    val channel: String,
    val placedAt: Instant,
    val pickupAt: Instant?,
    val notes: String?,
    val lines: List<PlacedLine>,
    /** Takeaway customer's name, shown on kitchen tickets. */
    val customerName: String? = null,
    /** Table label for dine-in orders (tables module, Phase 4). */
    val tableLabel: String? = null,
)

data class PlacedLine(
    val productId: UUID,
    val modifierOptionIds: List<UUID>,
    val productName: LocalizedText,
    val stationId: UUID?,
    val prepTimeSec: Int,
    val quantity: Int,
    val modifiers: List<LocalizedText>,
    val notes: String?,
)

/** Any visible change of an order's status or estimated ready time. */
data class OrderStatusChanged(
    val orderId: UUID,
    val number: String,
    val channel: String,
    val status: String,
    val estimatedReadyAt: Instant?,
    val at: Instant,
    /** Set for dine-in rounds, so the waiter app can follow its tables. */
    val tableSessionId: UUID? = null,
)

data class OrderCancelled(val orderId: UUID, val wasPaid: Boolean, val reason: String)

// ---------------------------------------------------------------- APIs for other modules

/** Used by the kitchen to move orders forward as tickets progress. */
interface OrderProgress {
    fun markPreparing(orderId: UUID)
    fun markReady(orderId: UUID)
    fun reopen(orderId: UUID)
    fun updateEstimatedReadyAt(orderId: UUID, estimatedReadyAt: Instant)
}

/** Used by payments. */
interface OrderPayments {
    /** Returns the order when the caller may pay it (staff or valid tracking token) and it awaits payment. */
    fun payableOrder(orderId: UUID, trackingToken: String?): PayableOrder
    fun markPaid(orderId: UUID)
    fun markRefunded(orderId: UUID)
}

data class PayableOrder(val orderId: UUID, val number: String, val total: BigDecimal, val currency: String)

/** Read access for notifications (SSE) and the queue display. */
interface OrderQueries {
    /** The order's current status when the caller is staff or holds the tracking token; null otherwise. */
    fun statusFor(orderId: UUID, trackingToken: String?): OrderStatusChanged?
    fun queueBoard(): QueueBoardView
}

data class QueueBoardView(val preparing: List<String>, val ready: List<String>)

/** Used by the tables module: each round sent from a table is a DINE_IN order of its session. */
interface TableOrders {
    /** Places a round for the kitchen and bar and returns the new order's id. */
    fun placeRound(sessionId: UUID, tableLabel: String, lines: List<RoundLine>, notes: String?, locale: Locale): UUID
    fun sessionOrders(sessionIds: Collection<UUID>): List<SessionOrder>
    /** The bill is paid: every round that was not cancelled is marked paid and served. */
    fun settle(sessionId: UUID)
    /** Merging tables: the rounds of one session move to another. */
    fun moveToSession(fromSessionId: UUID, toSessionId: UUID)
}

data class RoundLine(val productId: UUID, val quantity: Int, val modifierOptionIds: List<UUID>, val notes: String?)

data class SessionOrder(
    val orderId: UUID,
    val sessionId: UUID,
    val number: String,
    val status: String,
    val placedAt: Instant?,
    val readyAt: Instant?,
    val total: BigDecimal,
    val currency: String,
    val lines: List<SessionOrderLine>,
)

data class SessionOrderLine(
    val lineId: UUID,
    val productName: LocalizedText,
    val modifiers: List<LocalizedText>,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val total: BigDecimal,
    val notes: String?,
)
