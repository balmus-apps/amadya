package ro.amadya.notifications.internal

import org.springframework.http.ResponseEntity
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import ro.amadya.contract.model.QueueBoard
import ro.amadya.ordering.OrderQueries
import ro.amadya.ordering.OrderStatusChanged
import ro.amadya.shared.NotFoundException
import java.time.ZoneOffset
import java.util.UUID
import ro.amadya.contract.model.OrderStatus as OrderStatusDto
import ro.amadya.contract.model.OrderStatusChanged as OrderStatusChangedDto

/** Pushes order status changes to the tracking page and the queue display. */
@Component
class OrderNotifications(private val hub: SseHub, private val orders: OrderQueries) {

    @ApplicationModuleListener
    fun on(event: OrderStatusChanged) {
        hub.publishOrder(event.orderId, event.toDto())
        if (event.channel != "DINE_IN") hub.publishQueue(board())
        // Phase 2: Expo / Web Push when status becomes READY (needs push tokens from the customer apps).
    }

    fun board(): QueueBoard = orders.queueBoard().let { QueueBoard(it.preparing, it.ready) }
}

internal fun OrderStatusChanged.toDto() = OrderStatusChangedDto(
    orderId = orderId,
    number = number,
    status = OrderStatusDto.forValue(status),
    at = at.atOffset(ZoneOffset.UTC),
    estimatedReadyAt = estimatedReadyAt?.atOffset(ZoneOffset.UTC),
)

/** Hand-written (tag `streams`): the generated interface cannot express SSE return types. */
@RestController
class StreamsController(private val hub: SseHub, private val orders: OrderQueries, private val notifications: OrderNotifications) {

    @GetMapping("/orders/{orderId}/events", produces = ["text/event-stream"])
    fun streamOrderEvents(@PathVariable orderId: UUID, @RequestParam(required = false) token: String?): ResponseEntity<SseEmitter> {
        val current = orders.statusFor(orderId, token) ?: throw NotFoundException("Order")
        val emitter = hub.subscribeOrder(orderId)
        hub.send(emitter, SseHub.EVENT_ORDER, current.toDto())
        return ResponseEntity.ok().header("X-Accel-Buffering", "no").body(emitter)
    }

    @GetMapping("/queue/events", produces = ["text/event-stream"])
    fun streamQueueEvents(): ResponseEntity<SseEmitter> {
        val emitter = hub.subscribeQueue()
        hub.send(emitter, SseHub.EVENT_QUEUE, notifications.board())
        return ResponseEntity.ok().header("X-Accel-Buffering", "no").body(emitter)
    }
}
