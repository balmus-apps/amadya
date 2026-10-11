package ro.amadya.payments

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Payments for a table session, used by the tables module: taken by staff, or paid online by a guest. */
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

    /**
     * Starts an online payment with the provider (wallet sheet, ADR 0007). Until it is captured its [allocations] are
     * reserved; [SessionPaymentCaptured] or [SessionPaymentEnded] follow.
     */
    fun startOnline(sessionId: UUID, amount: BigDecimal, currency: String, label: String, allocations: List<LineAllocation>): OnlineSessionPayment

    /** Abandons an online payment still pending (e.g. the guest started a new one); its units are free again. */
    fun cancelPending(paymentId: UUID)

    /** Gives back a captured online payment that is no longer needed (the bill was paid meanwhile). */
    fun refund(paymentId: UUID)

    /** Undoes a payment recorded by mistake; returns false when it does not belong to the session or was already voided. */
    fun void(sessionId: UUID, paymentId: UUID): Boolean

    fun forSessions(sessionIds: Collection<UUID>): List<SessionPayment>
}

enum class InPersonMethod { CASH, CARD_POS }

enum class SessionPaymentState { PENDING, CAPTURED, VOIDED, FAILED, REFUNDED }

data class LineAllocation(val orderLineId: UUID, val quantity: Int, val amount: BigDecimal)

data class OnlineSessionPayment(val paymentId: UUID, val provider: String, val clientSecret: String?, val publishableKey: String?)

data class SessionPayment(
    val id: UUID,
    val sessionId: UUID,
    /** CASH, CARD_POS or ONLINE. */
    val method: String,
    val amount: BigDecimal,
    val currency: String,
    val tendered: BigDecimal?,
    val state: SessionPaymentState,
    val createdAt: Instant,
    val allocations: List<LineAllocation>,
) {
    val voided get() = state == SessionPaymentState.VOIDED
}

/** An online payment for a table session was captured by the provider (webhook or simulation). */
data class SessionPaymentCaptured(val paymentId: UUID, val sessionId: UUID, val amount: BigDecimal, val currency: String)

/** An online payment for a table session ended without money: failed, cancelled or expired. Its units are free again. */
data class SessionPaymentEnded(val paymentId: UUID, val sessionId: UUID)
