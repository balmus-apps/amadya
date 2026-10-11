package ro.amadya.tables.internal

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import ro.amadya.contract.model.TableEvent
import ro.amadya.ordering.OrderStatusChanged
import java.time.Duration
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Live feed for the waiter apps: which session changed, and when a round is ready to serve. Events carry no bill
 * data, only what to refresh, so sending them needs no database access and cannot hold up the committing thread.
 */
@Component
class TablesStream {
    private val log = LoggerFactory.getLogger(javaClass)
    private val subscribers = CopyOnWriteArraySet<SseEmitter>()

    fun subscribe(): SseEmitter {
        val emitter = SseEmitter(Duration.ofMinutes(30).toMillis())
        emitter.onCompletion { subscribers.remove(emitter) }
        emitter.onTimeout { subscribers.remove(emitter); emitter.complete() }
        emitter.onError { subscribers.remove(emitter) }
        subscribers.add(emitter)
        return emitter
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: TableChanged) =
        send(TableEvent(event.sessionId, TableEvent.Kind.forValue(event.kind.name), event.tableId, event.orderNumber))

    /** Kitchen progress on a dine-in round. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: OrderStatusChanged) {
        val sessionId = event.tableSessionId ?: return
        val kind = if (event.status == "READY") TableEvent.Kind.ROUND_READY else TableEvent.Kind.UPDATED
        send(TableEvent(sessionId, kind, orderNumber = event.number.takeIf { kind == TableEvent.Kind.ROUND_READY }))
    }

    /** Keeps proxies and phones' Wi-Fi from closing idle streams. */
    @Scheduled(fixedRate = 20_000)
    fun heartbeat() = subscribers.forEach {
        try {
            it.send(SseEmitter.event().comment("ping"))
        } catch (e: Exception) {
            it.completeWithError(e)
        }
    }

    private fun send(payload: TableEvent) = subscribers.forEach {
        try {
            it.send(SseEmitter.event().name("table").data(payload, MediaType.APPLICATION_JSON))
        } catch (e: Exception) {
            log.debug("Dropping table subscriber: {}", e.message)
            it.completeWithError(e)
        }
    }
}

/** Hand-written (tag `streams`): SSE cannot be expressed by the generated interfaces. */
@RestController
class TablesStreamController(private val stream: TablesStream) {
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WAITER','CASHIER')")
    @GetMapping("/tables/events", produces = ["text/event-stream"])
    fun streamTableEvents(): ResponseEntity<SseEmitter> = ResponseEntity.ok().header("X-Accel-Buffering", "no").body(stream.subscribe())
}
