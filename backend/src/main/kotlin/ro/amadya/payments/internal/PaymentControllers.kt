package ro.amadya.payments.internal

import com.stripe.exception.SignatureVerificationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.PaymentsApi
import ro.amadya.contract.model.PaymentIntent
import ro.amadya.shared.DomainException
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@RestController
class PaymentsController(private val payments: PaymentService) : PaymentsApi {

    override fun createPaymentIntent(orderId: UUID, token: String?): ResponseEntity<PaymentIntent> =
        ResponseEntity.ok(payments.startOnlinePayment(orderId, token))

    override fun simulatePaymentCapture(paymentId: UUID): ResponseEntity<Unit> {
        payments.simulateCapture(paymentId)
        return ResponseEntity.noContent().build()
    }
}

/** Hand-written (tag `webhooks`): needs the raw body to verify the Stripe signature. */
@RestController
class StripeWebhookController(
    private val stripe: ObjectProvider<StripePaymentProvider>,
    private val payments: PaymentService,
    private val json: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/payments/webhooks/stripe")
    fun stripeWebhook(@RequestHeader("Stripe-Signature") signature: String, @RequestBody payload: String): ResponseEntity<Unit> {
        val provider = stripe.ifAvailable ?: throw DomainException(org.springframework.http.HttpStatus.NOT_FOUND, "payment.provider_not_configured")
        val event = try {
            provider.verifyWebhook(payload, signature)
        } catch (_: SignatureVerificationException) {
            throw DomainException(org.springframework.http.HttpStatus.BAD_REQUEST, "payment.invalid_webhook")
        }
        val intent = json.readTree(payload).path("data").path("object")
        val intentId = intent.path("id").asString()
        when (event.type) {
            "payment_intent.succeeded" -> payments.capturedByRef("stripe", intentId)
            "payment_intent.payment_failed" ->
                payments.failedByRef("stripe", intentId, intent.path("last_payment_error").path("message").asString(null))
            else -> log.debug("Ignoring Stripe event {}", event.type)
        }
        return ResponseEntity.ok().build()
    }
}
