package ro.amadya.notifications.internal

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * In-memory registry of Server-Sent Event subscribers.
 * One API instance per install (ADR 0004), so no broker is needed; a multi-instance setup would fan out via Postgres LISTEN/NOTIFY.
 */
@Component
class SseHub {
    private val log = LoggerFactory.getLogger(javaClass)
    private val orderSubscribers = ConcurrentHashMap<UUID, MutableSet<SseEmitter>>()
    private val queueSubscribers = CopyOnWriteArraySet<SseEmitter>()

    fun subscribeOrder(orderId: UUID): SseEmitter = newEmitter { orderSubscribers[orderId]?.remove(it) }
        .also { orderSubscribers.computeIfAbsent(orderId) { CopyOnWriteArraySet() }.add(it) }

    fun subscribeQueue(): SseEmitter = newEmitter { queueSubscribers.remove(it) }.also { queueSubscribers.add(it) }

    fun publishOrder(orderId: UUID, data: Any) {
        orderSubscribers[orderId]?.forEach { send(it, EVENT_ORDER, data) }
    }

    fun publishQueue(data: Any) = queueSubscribers.forEach { send(it, EVENT_QUEUE, data) }

    fun send(emitter: SseEmitter, name: String, data: Any) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON))
        } catch (e: Exception) {
            log.debug("Dropping SSE subscriber: {}", e.message)
            emitter.completeWithError(e)
        }
    }

    /** Keeps proxies (Caddy, mobile networks) from closing idle streams. */
    @Scheduled(fixedRate = 20_000)
    fun heartbeat() {
        (orderSubscribers.values.flatten() + queueSubscribers).forEach {
            try {
                it.send(SseEmitter.event().comment("ping"))
            } catch (e: Exception) {
                it.completeWithError(e)
            }
        }
    }

    fun subscriberCount() = orderSubscribers.values.sumOf { it.size } + queueSubscribers.size

    private fun newEmitter(onDone: (SseEmitter) -> Unit): SseEmitter {
        val emitter = SseEmitter(TIMEOUT.toMillis())
        emitter.onCompletion { onDone(emitter) }
        emitter.onTimeout { onDone(emitter); emitter.complete() }
        emitter.onError { onDone(emitter) }
        return emitter
    }

    companion object {
        const val EVENT_ORDER = "order-status"
        const val EVENT_QUEUE = "queue"
        val TIMEOUT: Duration = Duration.ofMinutes(30)
    }
}
