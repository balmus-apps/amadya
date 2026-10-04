package ro.amadya.kitchen.internal

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.kitchen.KitchenTicketCreated
import ro.amadya.kitchen.TicketLine
import ro.amadya.ordering.OrderCancelled
import ro.amadya.ordering.OrderPlaced
import ro.amadya.ordering.OrderProgress
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.ConflictException
import ro.amadya.shared.LocalizedText
import ro.amadya.shared.NotFoundException
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
@Transactional(readOnly = true)
class KitchenService(
    private val tickets: KitchenTicketRepository,
    private val orders: OrderProgress,
    private val settings: RestaurantSettingsApi,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val open = listOf(TicketStatus.QUEUED, TicketStatus.IN_PROGRESS)

    /**
     * Splits a placed order into one ticket per station and estimates when it will be ready:
     * own preparation time plus the wait caused by tickets already queued at the station.
     */
    @ApplicationModuleListener
    fun on(event: OrderPlaced) {
        if (tickets.existsByOrderId(event.orderId)) return // event republished after a restart
        val now = clock.instant()
        val byStation = event.lines.filter { it.stationId != null }.groupBy { it.stationId!! }
        if (byStation.isEmpty()) {
            // Nothing to prepare (e.g. only bottled drinks): ready right away.
            orders.markReady(event.orderId)
            return
        }
        val created = byStation.map { (stationId, lines) ->
            val prep = lines.maxOf { it.prepTimeSec }.toLong()
            val load = tickets.countByStationIdAndStatusIn(stationId, open)
            val slots = settings.station(stationId)?.parallelSlots?.coerceAtLeast(1) ?: 1
            val wait = (load / slots) * prep
            tickets.save(
                KitchenTicketEntity(
                    orderId = event.orderId,
                    orderNumber = event.number,
                    channel = event.channel,
                    stationId = stationId,
                    prepTimeSec = prep.toInt(),
                    queuedAt = now,
                    estimatedReadyAt = now.plus(Duration.ofSeconds(prep + wait)),
                    notes = event.notes,
                    customerName = event.customerName?.substringBefore(' '),
                    tableLabel = event.tableLabel,
                    lines = lines.mapIndexed { i, l ->
                        KitchenTicketLineEntity(
                            position = i + 1,
                            productName = l.productName.toMap(),
                            quantity = l.quantity,
                            modifiers = l.modifiers.map { it.toMap() },
                            notes = l.notes,
                        )
                    }.toMutableList(),
                ),
            )
        }
        created.forEach {
            events.publishEvent(it.toEvent())
            events.publishEvent(KitchenTicketChanged(it.id, it.stationId))
        }
        orders.updateEstimatedReadyAt(event.orderId, created.maxOf { it.estimatedReadyAt })
        log.info("Order {}: {} kitchen ticket(s) queued", event.number, created.size)
    }

    @ApplicationModuleListener
    fun on(event: OrderCancelled) {
        val removed = tickets.findAllByOrderId(event.orderId)
        tickets.deleteAll(removed)
        removed.forEach { events.publishEvent(KitchenTicketChanged(it.id, it.stationId, removed = true)) }
    }

    fun find(id: UUID): KitchenTicketEntity? = tickets.findByIdOrNull(id)

    fun openTickets(stationId: UUID?): List<KitchenTicketEntity> =
        tickets.findAllByStatusInOrReadyAtAfterOrderByQueuedAtAsc(open, clock.instant().minus(Duration.ofMinutes(10)))
            .filter { stationId == null || it.stationId == stationId }

    @Transactional
    fun start(ticketId: UUID): KitchenTicketEntity {
        val ticket = load(ticketId)
        move(ticket, TicketStatus.IN_PROGRESS, TicketStatus.QUEUED)
        ticket.startedAt = clock.instant()
        orders.markPreparing(ticket.orderId)
        events.publishEvent(KitchenTicketChanged(ticket.id, ticket.stationId))
        return ticket
    }

    @Transactional
    fun bump(ticketId: UUID): KitchenTicketEntity {
        val ticket = load(ticketId)
        move(ticket, TicketStatus.READY, TicketStatus.QUEUED, TicketStatus.IN_PROGRESS)
        val now = clock.instant()
        ticket.startedAt = ticket.startedAt ?: now
        ticket.readyAt = now
        if (tickets.findAllByOrderId(ticket.orderId).all { it.status == TicketStatus.READY }) {
            orders.markReady(ticket.orderId)
        } else {
            orders.markPreparing(ticket.orderId)
        }
        events.publishEvent(KitchenTicketChanged(ticket.id, ticket.stationId))
        return ticket
    }

    @Transactional
    fun recall(ticketId: UUID): KitchenTicketEntity {
        val ticket = load(ticketId)
        move(ticket, TicketStatus.IN_PROGRESS, TicketStatus.READY)
        ticket.readyAt = null
        orders.reopen(ticket.orderId)
        events.publishEvent(KitchenTicketChanged(ticket.id, ticket.stationId))
        return ticket
    }

    private fun move(ticket: KitchenTicketEntity, to: TicketStatus, vararg from: TicketStatus) {
        if (ticket.status !in from) throw ConflictException("ticket.invalid_transition", ticket.status, to)
        ticket.status = to
    }

    private fun load(id: UUID) = tickets.findByIdOrNull(id) ?: throw NotFoundException("Ticket")

    private fun KitchenTicketEntity.toEvent() = KitchenTicketCreated(
        ticketId = id,
        orderId = orderId,
        orderNumber = orderNumber,
        channel = channel,
        stationId = stationId,
        queuedAt = queuedAt,
        estimatedReadyAt = estimatedReadyAt,
        notes = notes,
        lines = lines.map { l ->
            TicketLine(LocalizedText.fromMap(l.productName)!!, l.quantity, l.modifiers.mapNotNull { LocalizedText.fromMap(it) }, l.notes)
        },
    )
}
