package ro.amadya.payments.internal

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.contract.model.PaymentIntent
import ro.amadya.ordering.OrderCancelled
import ro.amadya.ordering.OrderPayments
import ro.amadya.ordering.OrderSettledInPerson
import ro.amadya.payments.SessionPaymentCaptured
import ro.amadya.payments.SessionPaymentEnded
import ro.amadya.shared.ConflictException
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.toMoney
import java.time.Clock
import java.util.UUID

@Service
class PaymentService(
    private val payments: PaymentRepository,
    private val provider: PaymentProvider,
    private val orders: OrderPayments,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Creates the provider intent once per order and returns the same one when the customer retries. */
    @Transactional
    fun startOnlinePayment(orderId: UUID, trackingToken: String?): PaymentIntent {
        val order = orders.payableOrder(orderId, trackingToken)
        val existing = payments.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, PaymentState.PENDING)
            ?.takeIf { it.amount.compareTo(order.total) == 0 && it.provider == provider.name }
        val payment = existing ?: payments.save(
            PaymentEntity(orderId = orderId, method = PaymentMethod.ONLINE, provider = provider.name, amount = order.total, currency = order.currency),
        )
        val clientSecret = if (payment.providerRef == null) {
            provider.createIntent(payment, "Order ${order.number}").also { payment.providerRef = it.providerRef }.clientSecret
        } else {
            provider.clientSecret(payment.providerRef!!)
        }
        return PaymentIntent(
            paymentId = payment.id,
            provider = PaymentIntent.Provider.forValue(provider.name),
            amount = payment.amount.toMoney(payment.currency),
            clientSecret = clientSecret,
            publishableKey = provider.publishableKey,
        )
    }

    /** Idempotent: webhooks may be delivered more than once. */
    @Transactional
    fun captured(payment: PaymentEntity) {
        if (payment.status == PaymentState.CAPTURED || payment.status == PaymentState.REFUNDED) return
        payment.status = PaymentState.CAPTURED
        payment.capturedAt = clock.instant()
        val sessionId = payment.tableSessionId
        if (sessionId != null) {
            // Paid from a guest's phone at the table: the tables module settles the bill (or refunds a late capture).
            events.publishEvent(SessionPaymentCaptured(payment.id, sessionId, payment.amount, payment.currency))
            log.info("Payment {} captured for table session {}", payment.id, sessionId)
            return
        }
        orders.markPaid(requireNotNull(payment.orderId) { "online payment without an order" })
        log.info("Payment {} captured for order {}", payment.id, payment.orderId)
    }

    @Transactional
    fun capturedByRef(providerName: String, providerRef: String) {
        payments.findByProviderAndProviderRef(providerName, providerRef)?.let(::captured)
            ?: log.warn("Capture for unknown {} payment {}", providerName, providerRef)
    }

    @Transactional
    fun failedByRef(providerName: String, providerRef: String, reason: String?) {
        payments.findByProviderAndProviderRef(providerName, providerRef)?.takeIf { it.status == PaymentState.PENDING }?.let {
            it.status = PaymentState.FAILED
            it.failure = reason
            it.tableSessionId?.let { sessionId -> events.publishEvent(SessionPaymentEnded(it.id, sessionId)) }
        }
    }

    @Transactional
    fun simulateCapture(paymentId: UUID) {
        if (provider !is FakePaymentProvider) throw ConflictException("payment.simulation_disabled")
        captured(payments.findByIdOrNull(paymentId) ?: throw NotFoundException("Payment"))
    }

    /** Staff took the payment at pickup: keep the record next to online payments (the order is already PAID). */
    @ApplicationModuleListener
    fun on(event: OrderSettledInPerson) {
        val payment = payments.save(
            PaymentEntity(
                orderId = event.orderId,
                method = PaymentMethod.valueOf(event.method),
                provider = "in_person",
                amount = event.amount,
                currency = event.currency,
                status = PaymentState.CAPTURED,
                tendered = event.tendered,
                recordedBy = event.recordedBy,
            ),
        )
        payment.capturedAt = clock.instant()
        log.info("Pickup payment {} {} {} {} recorded for order {}", payment.id, event.method, event.amount, event.currency, event.orderId)
    }

    /** A paid order was cancelled (by staff or after a late payment): give the money back. */
    @ApplicationModuleListener
    fun on(event: OrderCancelled) {
        if (!event.wasPaid) return
        val payment = payments.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(event.orderId, PaymentState.CAPTURED) ?: return
        provider.refund(payment)
        payment.status = PaymentState.REFUNDED
        payment.refundedAt = clock.instant()
        orders.markRefunded(event.orderId)
        log.info("Payment {} refunded: {}", payment.id, event.reason)
    }
}
