package ro.amadya.tables.internal

import org.slf4j.LoggerFactory
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import ro.amadya.contract.api.TableGuestApi
import ro.amadya.contract.model.AddRoundRequest
import ro.amadya.contract.model.GuestPaymentRequest
import ro.amadya.contract.model.GuestTable
import ro.amadya.contract.model.GuestView
import ro.amadya.contract.model.JoinTableRequest
import ro.amadya.contract.model.JoinTableResult
import ro.amadya.contract.model.PaymentIntent
import ro.amadya.contract.model.TableEvent
import ro.amadya.ordering.OrderStatusChanged
import ro.amadya.shared.UnprocessableException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/** Public (no login): guests are identified by the device token in X-Table-Guest. Transactional for lazy order lines. */
@RestController
@Transactional
class GuestController(private val service: GuestService) : TableGuestApi {
    private fun locale() = LocaleContextHolder.getLocale()

    override fun getGuestTable(code: String, acceptLanguage: String?): ResponseEntity<GuestTable> = ResponseEntity.ok(service.table(code, locale()))

    override fun joinTable(code: String, joinTableRequest: JoinTableRequest, acceptLanguage: String?): ResponseEntity<JoinTableResult> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.join(code, joinTableRequest.name, locale()))

    override fun getGuestView(code: String, xTableGuest: String, acceptLanguage: String?): ResponseEntity<GuestView> =
        ResponseEntity.ok(service.view(xTableGuest, locale()))

    override fun sendGuestRound(code: String, xTableGuest: String, addRoundRequest: AddRoundRequest, acceptLanguage: String?): ResponseEntity<GuestView> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.sendRound(xTableGuest, addRoundRequest, locale()))

    override fun ownerDecideGuest(code: String, guestId: UUID, action: String, xTableGuest: String, acceptLanguage: String?): ResponseEntity<GuestView> {
        if (action != "approve" && action != "reject") throw UnprocessableException("tables.unknown_action")
        return ResponseEntity.ok(service.decide(xTableGuest, guestId, action == "approve", locale()))
    }

    override fun startGuestPayment(code: String, xTableGuest: String, guestPaymentRequest: GuestPaymentRequest, acceptLanguage: String?): ResponseEntity<PaymentIntent> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.pay(xTableGuest, guestPaymentRequest, locale()))
}

/**
 * Live feed for the guests of each table: which session changed. Like the waiters' feed, events carry no bill data, so
 * sending them needs no database access.
 */
@Component
class GuestStream {
    private val log = LoggerFactory.getLogger(javaClass)
    private val subscribers = ConcurrentHashMap<UUID, MutableSet<SseEmitter>>()

    fun subscribe(sessionId: UUID): SseEmitter {
        val emitter = SseEmitter(Duration.ofMinutes(30).toMillis())
        val remove = { subscribers[sessionId]?.remove(emitter); Unit }
        emitter.onCompletion(remove)
        emitter.onTimeout { remove(); emitter.complete() }
        emitter.onError { remove() }
        subscribers.computeIfAbsent(sessionId) { CopyOnWriteArraySet() }.add(emitter)
        return emitter
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: TableChanged) {
        // Guests only need to know that something changed; waiter-only kinds are plain updates for them.
        val kind = if (event.kind == TableChanged.Kind.CLOSED) TableEvent.Kind.CLOSED else TableEvent.Kind.UPDATED
        send(event.sessionId, TableEvent(event.sessionId, kind))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: OrderStatusChanged) {
        val sessionId = event.tableSessionId ?: return
        val kind = if (event.status == "READY") TableEvent.Kind.ROUND_READY else TableEvent.Kind.UPDATED
        send(sessionId, TableEvent(sessionId, kind, orderNumber = event.number.takeIf { kind == TableEvent.Kind.ROUND_READY }))
    }

    @Scheduled(fixedRate = 20_000)
    fun heartbeat() = subscribers.values.flatten().forEach {
        try {
            it.send(SseEmitter.event().comment("ping"))
        } catch (e: Exception) {
            it.completeWithError(e)
        }
    }

    private fun send(sessionId: UUID, payload: TableEvent) = subscribers[sessionId]?.forEach {
        try {
            it.send(SseEmitter.event().name("table").data(payload, MediaType.APPLICATION_JSON))
        } catch (e: Exception) {
            log.debug("Dropping guest subscriber: {}", e.message)
            it.completeWithError(e)
        }
    }
}

/** Hand-written (tag `streams`): SSE cannot be expressed by the generated interfaces. */
@RestController
class GuestStreamController(private val stream: GuestStream, private val service: GuestService) {
    @GetMapping("/t/{code}/events", produces = ["text/event-stream"])
    fun streamGuestEvents(@PathVariable code: String, @RequestHeader("X-Table-Guest") token: String): ResponseEntity<SseEmitter> =
        ResponseEntity.ok().header("X-Accel-Buffering", "no").body(stream.subscribe(service.sessionOf(token)))
}
