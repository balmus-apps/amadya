package ro.amadya.kitchen.internal

import org.slf4j.LoggerFactory
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import ro.amadya.contract.model.KitchenTicket
import ro.amadya.contract.model.KitchenTicketEvent
import ro.amadya.contract.model.KitchenTicketLine
import ro.amadya.shared.localized
import java.time.Duration
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import ro.amadya.contract.model.KitchenTicketStatus as KitchenTicketStatusDto
import ro.amadya.contract.model.OrderChannel as OrderChannelDto

/** Internal event: a ticket was created, moved or removed; pushed to the displays of its station after commit. */
data class KitchenTicketChanged(val ticketId: UUID, val stationId: UUID, val removed: Boolean = false)

@Component
class KitchenTicketMapper {
    fun toDto(t: KitchenTicketEntity, locale: Locale) = KitchenTicket(
        id = t.id,
        orderId = t.orderId,
        orderNumber = t.orderNumber,
        channel = OrderChannelDto.forValue(t.channel),
        stationId = t.stationId,
        status = KitchenTicketStatusDto.forValue(t.status.name),
        queuedAt = t.queuedAt.atOffset(ZoneOffset.UTC),
        estimatedReadyAt = t.estimatedReadyAt.atOffset(ZoneOffset.UTC),
        lines = t.lines.map { l -> KitchenTicketLine(l.productName.localized(locale), l.quantity, l.modifiers.map { it.localized(locale) }, l.notes) },
        startedAt = t.startedAt?.atOffset(ZoneOffset.UTC),
        readyAt = t.readyAt?.atOffset(ZoneOffset.UTC),
        notes = t.notes,
        customerName = t.customerName,
        tableLabel = t.tableLabel,
    )
}

/**
 * Live feed for kitchen and bar displays: one channel per station and language.
 * A single API instance per install (ADR 0004), so subscribers are kept in memory.
 */
@Component
class KitchenStream(private val kitchen: KitchenService, private val mapper: KitchenTicketMapper) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val subscribers = ConcurrentHashMap<Pair<UUID, String>, MutableSet<SseEmitter>>()

    fun subscribe(stationId: UUID, locale: Locale): SseEmitter {
        val key = stationId to language(locale)
        val emitter = SseEmitter(Duration.ofMinutes(30).toMillis())
        val remove = { subscribers[key]?.remove(emitter); Unit }
        emitter.onCompletion(remove)
        emitter.onTimeout { remove(); emitter.complete() }
        emitter.onError { remove() }
        subscribers.computeIfAbsent(key) { CopyOnWriteArraySet() }.add(emitter)
        return emitter
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: KitchenTicketChanged) {
        val ticket = if (event.removed) null else kitchen.find(event.ticketId)
        subscribers.filterKeys { it.first == event.stationId }.forEach { (key, emitters) ->
            val payload = KitchenTicketEvent(
                type = if (ticket == null) KitchenTicketEvent.Type.REMOVED else KitchenTicketEvent.Type.UPSERT,
                ticketId = event.ticketId,
                stationId = event.stationId,
                ticket = ticket?.let { mapper.toDto(it, Locale.of(key.second)) },
            )
            emitters.forEach { send(it, payload) }
        }
    }

    /** Keeps proxies and tablets' Wi-Fi from closing idle streams. */
    @Scheduled(fixedRate = 20_000)
    fun heartbeat() = subscribers.values.flatten().forEach {
        try {
            it.send(SseEmitter.event().comment("ping"))
        } catch (e: Exception) {
            it.completeWithError(e)
        }
    }

    private fun send(emitter: SseEmitter, payload: KitchenTicketEvent) {
        try {
            emitter.send(SseEmitter.event().name("ticket").data(payload, MediaType.APPLICATION_JSON))
        } catch (e: Exception) {
            log.debug("Dropping kitchen subscriber: {}", e.message)
            emitter.completeWithError(e)
        }
    }

    private fun language(locale: Locale) = if (locale.language == "en") "en" else "ro"
}

/** Hand-written (tag `streams`): SSE cannot be expressed by the generated interfaces. */
@RestController
class KitchenStreamController(private val stream: KitchenStream) {
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','KITCHEN')")
    @GetMapping("/kitchen/events", produces = ["text/event-stream"])
    fun streamKitchenEvents(@RequestParam stationId: UUID): ResponseEntity<SseEmitter> =
        ResponseEntity.ok().header("X-Accel-Buffering", "no").body(stream.subscribe(stationId, LocaleContextHolder.getLocale()))
}
