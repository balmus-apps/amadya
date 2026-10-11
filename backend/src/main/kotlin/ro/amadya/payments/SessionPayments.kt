package ro.amadya.payments

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Cash and card-terminal payments taken at a table, used by the tables module. */
interface SessionPayments {
    /** Records a captured payment; [allocations] are the units it covers when the bill is split item by item. */
    fun record(
        sessionId: UUID,
        method: InPersonMethod,
        amount: BigDecimal,
        currency: String,
        tendered: BigDecimal?,
        allocations: List<LineAllocation>,
    ): UUID

    /** Undoes a payment recorded by mistake; returns false when it does not belong to the session or was already voided. */
    fun void(sessionId: UUID, paymentId: UUID): Boolean

    fun forSessions(sessionIds: Collection<UUID>): List<SessionPayment>
}

enum class InPersonMethod { CASH, CARD_POS }

data class LineAllocation(val orderLineId: UUID, val quantity: Int, val amount: BigDecimal)

data class SessionPayment(
    val id: UUID,
    val sessionId: UUID,
    val method: InPersonMethod,
    val amount: BigDecimal,
    val currency: String,
    val tendered: BigDecimal?,
    val voided: Boolean,
    val createdAt: Instant,
    val allocations: List<LineAllocation>,
)
