package ro.amadya.payments.internal

import com.stripe.StripeClient
import com.stripe.net.RequestOptions
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.RefundCreateParams
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import ro.amadya.shared.Money

@ConfigurationProperties("amadya.payments")
data class PaymentsProperties(val provider: String = "fake", val stripe: Stripe = Stripe()) {
    data class Stripe(val secretKey: String = "", val publishableKey: String = "", val webhookSecret: String = "")
}

data class ProviderIntent(val providerRef: String, val clientSecret: String?)

/** Port to an online payment provider (ADR 0007). Implementations never see card data. */
interface PaymentProvider {
    val name: String
    val publishableKey: String?
    fun createIntent(payment: PaymentEntity, orderNumber: String): ProviderIntent
    fun clientSecret(providerRef: String): String?
    fun refund(payment: PaymentEntity)
}

/** Development provider: no external calls; capture is simulated through POST /payments/{id}/simulate-capture. */
@Component
@ConditionalOnProperty(name = ["amadya.payments.provider"], havingValue = "fake", matchIfMissing = true)
class FakePaymentProvider : PaymentProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    override val name = "fake"
    override val publishableKey: String? = null
    override fun createIntent(payment: PaymentEntity, orderNumber: String) = ProviderIntent("fake_${payment.id}", null)
    override fun clientSecret(providerRef: String): String? = null
    override fun refund(payment: PaymentEntity) = log.info("[fake] refund {} {} for order {}", payment.amount, payment.currency, payment.orderId)
}

/** Stripe PaymentIntents with automatic payment methods (card, Apple Pay, Google Pay). */
@Component
@ConditionalOnProperty(name = ["amadya.payments.provider"], havingValue = "stripe")
class StripePaymentProvider(private val props: PaymentsProperties) : PaymentProvider {

    val client: StripeClient = StripeClient(props.stripe.secretKey.ifBlank { error("STRIPE_SECRET_KEY is required when PAYMENT_PROVIDER=stripe") })

    override val name = "stripe"
    override val publishableKey: String? get() = props.stripe.publishableKey.ifBlank { null }

    override fun createIntent(payment: PaymentEntity, orderNumber: String): ProviderIntent {
        val params = PaymentIntentCreateParams.builder()
            .setAmount(Money.toMinorUnits(payment.amount))
            .setCurrency(payment.currency.lowercase())
            .setDescription("Order $orderNumber")
            .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
            .putMetadata("orderId", payment.orderId.toString())
            .putMetadata("paymentId", payment.id.toString())
            .build()
        // The payment id is the idempotency key, so a retried request never creates a second charge.
        val intent = client.v1().paymentIntents().create(params, RequestOptions.builder().setIdempotencyKey(payment.id.toString()).build())
        return ProviderIntent(intent.id, intent.clientSecret)
    }

    override fun clientSecret(providerRef: String): String? =
        client.v1().paymentIntents().retrieve(providerRef, RequestOptions.getDefault()).clientSecret

    override fun refund(payment: PaymentEntity) {
        client.v1().refunds().create(
            RefundCreateParams.builder().setPaymentIntent(payment.providerRef).build(),
            RequestOptions.builder().setIdempotencyKey("refund-${payment.id}").build(),
        )
    }

    fun verifyWebhook(payload: String, signature: String) =
        client.constructEvent(payload, signature, props.stripe.webhookSecret.ifBlank { error("STRIPE_WEBHOOK_SECRET is not configured") })
}
