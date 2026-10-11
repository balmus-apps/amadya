package ro.amadya.payments.internal

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.payments.InPersonMethod
import ro.amadya.payments.LineAllocation
import ro.amadya.payments.OnlineSessionPayment
import ro.amadya.payments.SessionPayment
import ro.amadya.payments.SessionPaymentEnded
import ro.amadya.payments.SessionPaymentState
import ro.amadya.payments.SessionPayments
import ro.amadya.shared.Actor
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Payments for table sessions: taken by staff (captured at once, no provider) or paid online by a guest through the
 * payment provider, like takeaway orders.
 */
@Service
@Transactional(readOnly = true)
class InPersonPaymentService(
    private val payments: PaymentRepository,
    private val provider: PaymentProvider,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) : SessionPayments {
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
    override fun startOnline(sessionId: UUID, amount: BigDecimal, currency: String, label: String, allocations: List<LineAllocation>): OnlineSessionPayment {
        val payment = payments.save(
            PaymentEntity(
                orderId = null,
                method = PaymentMethod.ONLINE,
                provider = provider.name,
                amount = amount,
                currency = currency,
                tableSessionId = sessionId,
                allocations = allocations.map { PaymentAllocationEntity(orderLineId = it.orderLineId, quantity = it.quantity, amount = it.amount) }.toMutableList(),
            ),
        )
        val intent = provider.createIntent(payment, label)
        payment.providerRef = intent.providerRef
        log.info("Online table payment {} {} {} started for session {}", payment.id, amount, currency, sessionId)
        return OnlineSessionPayment(payment.id, provider.name, intent.clientSecret, provider.publishableKey)
    }

    @Transactional
    override fun cancelPending(paymentId: UUID) {
        payments.findByIdOrNull(paymentId)?.takeIf { it.status == PaymentState.PENDING }?.let { end(it, "cancelled") }
    }

    @Transactional
    override fun refund(paymentId: UUID) {
        val payment = payments.findByIdOrNull(paymentId)?.takeIf { it.status == PaymentState.CAPTURED } ?: return
        provider.refund(payment)
        payment.status = PaymentState.REFUNDED
        payment.refundedAt = clock.instant()
        log.info("Online table payment {} refunded: the bill was already paid", paymentId)
    }

    /** A guest who walked away from the wallet sheet must not keep items reserved: pending payments expire. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    @Transactional
    fun expireAbandoned() {
        payments.findAllByStatusAndTableSessionIdIsNotNullAndCreatedAtBefore(PaymentState.PENDING, clock.instant().minus(PENDING_TTL))
            .forEach { end(it, "expired") }
    }

    private fun end(payment: PaymentEntity, reason: String) {
        payment.status = PaymentState.FAILED
        payment.failure = reason
        events.publishEvent(SessionPaymentEnded(payment.id, payment.tableSessionId!!))
    }

    @Transactional
    override fun void(sessionId: UUID, paymentId: UUID): Boolean {
        val payment = payments.findByIdOrNull(paymentId)
            ?.takeIf { it.tableSessionId == sessionId && it.status == PaymentState.CAPTURED && it.method != PaymentMethod.ONLINE }
            ?: return false
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
                method = it.method.name,
                amount = it.amount,
                currency = it.currency,
                tendered = it.tendered,
                state = SessionPaymentState.valueOf(it.status.name),
                createdAt = it.createdAt,
                allocations = it.allocations.map { a -> LineAllocation(a.orderLineId, a.quantity, a.amount) },
            )
        }
}

private val PENDING_TTL: Duration = Duration.ofMinutes(15)
