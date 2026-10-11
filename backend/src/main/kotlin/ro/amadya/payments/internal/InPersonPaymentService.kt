package ro.amadya.payments.internal

import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.payments.InPersonMethod
import ro.amadya.payments.LineAllocation
import ro.amadya.payments.SessionPayment
import ro.amadya.payments.SessionPayments
import ro.amadya.shared.Actor
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/** Payments taken by staff at the table: captured as soon as they are recorded, no provider involved. */
@Service
@Transactional(readOnly = true)
class InPersonPaymentService(private val payments: PaymentRepository, private val clock: Clock) : SessionPayments {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun record(
        sessionId: UUID,
        method: InPersonMethod,
        amount: BigDecimal,
        currency: String,
        tendered: BigDecimal?,
        allocations: List<LineAllocation>,
    ): UUID {
        val payment = PaymentEntity(
            orderId = null,
            method = PaymentMethod.valueOf(method.name),
            provider = "in_person",
            amount = amount,
            currency = currency,
            status = PaymentState.CAPTURED,
            tableSessionId = sessionId,
            tendered = tendered,
            recordedBy = Actor.current()?.userId,
            allocations = allocations.map { PaymentAllocationEntity(orderLineId = it.orderLineId, quantity = it.quantity, amount = it.amount) }.toMutableList(),
        )
        payment.capturedAt = clock.instant()
        payments.save(payment)
        log.info("Table payment {} {} {} {} recorded for session {}", payment.id, method, amount, currency, sessionId)
        return payment.id
    }

    @Transactional
    override fun void(sessionId: UUID, paymentId: UUID): Boolean {
        val payment = payments.findByIdOrNull(paymentId)?.takeIf { it.tableSessionId == sessionId && it.status == PaymentState.CAPTURED } ?: return false
        payment.status = PaymentState.VOIDED
        payment.voidedAt = clock.instant()
        log.info("Table payment {} voided", paymentId)
        return true
    }

    override fun forSessions(sessionIds: Collection<UUID>): List<SessionPayment> =
        if (sessionIds.isEmpty()) emptyList() else payments.findAllByTableSessionIdInOrderByCreatedAtAsc(sessionIds).map {
            SessionPayment(
                id = it.id,
                sessionId = it.tableSessionId!!,
                method = InPersonMethod.valueOf(it.method.name),
                amount = it.amount,
                currency = it.currency,
                tendered = it.tendered,
                voided = it.status == PaymentState.VOIDED,
                createdAt = it.createdAt,
                allocations = it.allocations.map { a -> LineAllocation(a.orderLineId, a.quantity, a.amount) },
            )
        }
}
