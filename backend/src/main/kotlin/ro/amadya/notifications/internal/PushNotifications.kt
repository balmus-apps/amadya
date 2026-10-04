package ro.amadya.notifications.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.MessageSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import ro.amadya.contract.api.NotificationsApi
import ro.amadya.contract.model.PushSubscriptionRequest
import ro.amadya.ordering.OrderQueries
import ro.amadya.ordering.OrderStatusChanged
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.Ids
import ro.amadya.shared.NotFoundException
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Entity
@Table(name = "push_subscription")
class PushSubscriptionEntity(
    @Id val id: UUID = Ids.newId(),
    @Column(name = "order_id") val orderId: UUID,
    @Column(name = "expo_token") val expoToken: String,
    var locale: String = "ro",
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
)

interface PushSubscriptionRepository : JpaRepository<PushSubscriptionEntity, UUID> {
    fun findAllByOrderId(orderId: UUID): List<PushSubscriptionEntity>
    fun findByOrderIdAndExpoToken(orderId: UUID, expoToken: String): PushSubscriptionEntity?
    fun deleteAllByOrderId(orderId: UUID)
}

data class PushMessage(val to: String, val title: String, val body: String, val data: Map<String, String>)

/** Port for push delivery, so tests (and future providers such as Web Push) can replace it. */
fun interface PushSender {
    fun send(messages: List<PushMessage>)
}

@ConfigurationProperties("amadya.notifications.expo")
data class ExpoPushProperties(val enabled: Boolean = true, val url: String = "https://exp.host/--/api/v2/push/send", val accessToken: String = "")

/** Expo push service: one HTTPS call delivers to both APNs (iOS) and FCM (Android). */
@Component
class ExpoPushSender(private val props: ExpoPushProperties) : PushSender {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.builder().baseUrl(props.url).build()

    override fun send(messages: List<PushMessage>) {
        if (messages.isEmpty()) return
        if (!props.enabled) {
            messages.forEach { log.info("[push disabled] {} -> {}", it.to, it.body) }
            return
        }
        val payload = messages.map { mapOf("to" to it.to, "title" to it.title, "body" to it.body, "data" to it.data, "sound" to "default", "priority" to "high") }
        try {
            client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .headers { h -> if (props.accessToken.isNotBlank()) h.setBearerAuth(props.accessToken) }
                .body(payload)
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            // A failed push must never break the order flow; the customer still sees the status live.
            log.warn("Expo push failed: {}", e.message)
        }
    }
}

@Component
class PushNotifications(
    private val subscriptions: PushSubscriptionRepository,
    private val sender: PushSender,
    private val settings: RestaurantSettingsApi,
    private val messages: MessageSource,
) {
    @ApplicationModuleListener
    fun on(event: OrderStatusChanged) {
        val key = when (event.status) {
            "READY" -> "push.ready.body"
            "CANCELLED" -> "push.cancelled.body"
            "COMPLETED" -> null
            else -> return
        }
        val subs = subscriptions.findAllByOrderId(event.orderId)
        if (key != null) {
            val title = settings.name()
            sender.send(
                subs.map {
                    PushMessage(it.expoToken, title, messages.getMessage(key, arrayOf(event.number), Locale.of(it.locale)), mapOf("orderId" to event.orderId.toString()))
                },
            )
        }
        // Tokens are only kept while the order is open.
        if (event.status != "READY") subscriptions.deleteAll(subs)
    }

    @Transactional
    fun register(orderId: UUID, token: String, locale: String) {
        val existing = subscriptions.findByOrderIdAndExpoToken(orderId, token)
        if (existing != null) existing.locale = locale else subscriptions.save(PushSubscriptionEntity(orderId = orderId, expoToken = token, locale = locale))
    }
}

@RestController
class PushSubscriptionController(private val orders: OrderQueries, private val push: PushNotifications) : NotificationsApi {
    override fun registerPushSubscription(orderId: UUID, pushSubscriptionRequest: PushSubscriptionRequest, token: String?): ResponseEntity<Unit> {
        orders.statusFor(orderId, token) ?: throw NotFoundException("Order")
        push.register(orderId, pushSubscriptionRequest.expoPushToken, pushSubscriptionRequest.locale?.value ?: "ro")
        return ResponseEntity.noContent().build()
    }
}
