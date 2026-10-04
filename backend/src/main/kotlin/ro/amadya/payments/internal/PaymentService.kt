package ro.amadya.payments.internal

import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.contract.model.PaymentIntent
import ro.amadya.ordering.OrderCancelled
import ro.amadya.ordering.OrderPayments
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
            provider.createIntent(payment, order.number).also { payment.providerRef = it.providerRef }.clientSecret
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
        orders.markPaid(payment.orderId)
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
        }
    }

    @Transactional
    fun simulateCapture(paymentId: UUID) {
        if (provider !is FakePaymentProvider) throw ConflictException("payment.simulation_disabled")
        captured(payments.findByIdOrNull(paymentId) ?: throw NotFoundException("Payment"))
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
