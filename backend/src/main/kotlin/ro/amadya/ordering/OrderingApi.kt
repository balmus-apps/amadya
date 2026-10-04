package ro.amadya.ordering

import ro.amadya.shared.LocalizedText
import java.math.BigDecimal
import java.time.Instant
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
