package ro.amadya.ordering.internal

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Limit
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.catalog.LineSelection
import ro.amadya.catalog.ProductCatalog
import ro.amadya.contract.model.CreateOrderRequest
import ro.amadya.ordering.OrderCancelled
import ro.amadya.ordering.OrderPayments
import ro.amadya.ordering.OrderPlaced
import ro.amadya.ordering.OrderProgress
import ro.amadya.ordering.OrderQueries
import ro.amadya.ordering.OrderStatusChanged
import ro.amadya.ordering.PayableOrder
import ro.amadya.ordering.PlacedLine
import ro.amadya.ordering.QueueBoardView
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.Actor
import ro.amadya.shared.AmadyaProperties
import ro.amadya.shared.ConflictException
import ro.amadya.shared.ForbiddenException
import ro.amadya.shared.LocalizedText
import ro.amadya.shared.Money
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.Roles
import ro.amadya.shared.UnprocessableException
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.Base64
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

@ConfigurationProperties("amadya.ordering")
data class OrderingProperties(val paymentTimeout: Duration = Duration.ofMinutes(15))

/** The created order together with the raw tracking token, which is only ever returned once. */
data class CreatedOrder(val order: OrderEntity, val trackingToken: String)

@Service
@Transactional(readOnly = true)
class OrderService(
    private val orders: OrderRepository,
    private val numbers: OrderNumberGenerator,
    private val catalog: ProductCatalog,
    private val settings: RestaurantSettingsApi,
    private val events: ApplicationEventPublisher,
    private val props: AmadyaProperties,
    private val orderingProps: OrderingProperties,
    private val clock: Clock,
) : OrderProgress, OrderPayments, OrderQueries {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    // ---------------------------------------------------------------- create
    @Transactional
    fun create(req: CreateOrderRequest, locale: Locale): CreatedOrder {
        val actor = Actor.current()
        val staff = actor?.isStaff == true
        val channel = OrderChannel.valueOf(req.channel.value)
        val features = settings.features()
        val now = clock.instant()

        when (channel) {
            OrderChannel.TAKEAWAY -> {
                if (!features.takeaway && !staff) throw UnprocessableException("order.takeaway_disabled")
                if (req.customer == null) throw UnprocessableException("order.customer_required")
                if (req.pickupAt != null && req.pickupAt!!.toInstant().isBefore(now)) throw UnprocessableException("order.pickup_in_past")
            }
            OrderChannel.COUNTER, OrderChannel.DINE_IN -> if (!staff) throw ForbiddenException("order.staff_only", channel.name)
        }

        val priced = catalog.priceLines(req.lines.map { LineSelection(it.productId, it.quantity, it.modifierOptionIds.orEmpty(), it.notes) })
        val lines = priced.mapIndexed { index, p ->
            val total = p.unitPrice.multiply(BigDecimal(p.quantity))
            OrderLineEntity(
                position = index + 1,
                productId = p.productId,
                productName = p.name.toMap(),
                stationId = p.stationId,
                prepTimeSec = p.prepTimeSec,
                quantity = p.quantity,
                unitPrice = p.unitPrice,
                vatPercent = p.vatPercent,
                total = total,
                vatAmount = Money.vatOfGross(total, p.vatPercent),
                notes = p.notes,
                modifiers = p.modifiers.map { OrderLineModifierEntity(optionId = it.optionId, name = it.name.toMap(), priceDelta = it.priceDelta) }.toMutableList(),
            )
        }

        // Guests pay online first when online payments are on; staff-created orders go straight to the kitchen.
        val awaitPayment = channel == OrderChannel.TAKEAWAY && features.onlinePayments && !staff
        val token = newToken()
        val order = OrderEntity(
            number = numbers.next(businessDate(), settings.orderNumberPrefix()),
            businessDate = businessDate(),
            channel = channel,
            status = if (awaitPayment) OrderStatus.PENDING_PAYMENT else OrderStatus.PLACED,
            customerName = req.customer?.name?.trim(),
            customerPhone = req.customer?.phone,
            customerEmail = req.customer?.email,
            customerUserId = actor?.takeIf { Roles.CUSTOMER in it.roles }?.userId,
            createdByUserId = actor?.takeIf { staff }?.userId,
            tableSessionId = req.tableSessionId,
            pickupAt = req.pickupAt?.toInstant(),
            notes = req.notes?.trim()?.ifBlank { null },
            locale = locale.language.takeIf { it == "en" } ?: "ro",
            currency = settings.currency(),
            total = lines.fold(BigDecimal.ZERO) { acc, l -> acc + l.total },
            vatTotal = lines.fold(BigDecimal.ZERO) { acc, l -> acc + l.vatAmount },
            trackingTokenHash = hash(token),
            lines = lines.toMutableList(),
        )
        orders.save(order)
        if (order.status == OrderStatus.PLACED) place(order) else statusChanged(order)
        log.info("Order {} created: channel={} status={} total={}", order.number, channel, order.status, order.total)
        return CreatedOrder(order, token)
    }

    // ---------------------------------------------------------------- queries
    fun get(orderId: UUID, trackingToken: String?): OrderEntity {
        val order = orders.findByIdOrNull(orderId) ?: throw NotFoundException("Order")
        if (!canAccess(order, trackingToken)) throw NotFoundException("Order")
        return order
    }

    fun list(statuses: Collection<OrderStatus>, channel: OrderChannel?, limit: Int): List<OrderEntity> =
        orders.search(statuses.ifEmpty { OrderStatus.entries }, channel, Limit.of(limit))

    override fun statusFor(orderId: UUID, trackingToken: String?): OrderStatusChanged? =
        orders.findByIdOrNull(orderId)?.takeIf { canAccess(it, trackingToken) }?.toEvent()

    override fun queueBoard(): QueueBoardView {
        val visible = orders.findAllByBusinessDateAndChannelInAndStatusInOrderByPlacedAtAsc(
            businessDate(),
            listOf(OrderChannel.TAKEAWAY, OrderChannel.COUNTER),
            listOf(OrderStatus.PLACED, OrderStatus.PREPARING, OrderStatus.READY),
        )
        return QueueBoardView(
            preparing = visible.filter { it.status != OrderStatus.READY }.map { it.number },
            ready = visible.filter { it.status == OrderStatus.READY }.map { it.number },
        )
    }

    // ---------------------------------------------------------------- staff actions
    @Transactional
    fun complete(orderId: UUID): OrderEntity {
        val order = load(orderId)
        transition(order, OrderStatus.COMPLETED, OrderStatus.READY)
        order.completedAt = clock.instant()
        statusChanged(order)
        return order
    }

    @Transactional
    fun cancel(orderId: UUID, reason: String): OrderEntity {
        val order = load(orderId)
        if (order.status in setOf(OrderStatus.PREPARING, OrderStatus.READY) && Actor.current()?.hasAny(Roles.ADMIN, Roles.MANAGER) != true) {
            throw ForbiddenException("order.cancel_requires_manager")
        }
        transition(order, OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, OrderStatus.PLACED, OrderStatus.PREPARING, OrderStatus.READY)
        cancelInternal(order, reason.trim())
        return order
    }

    /** Takeaway orders not paid within the timeout are cancelled so their number leaves the system. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    @Transactional
    fun expireUnpaidOrders() {
        val cutoff = clock.instant().minus(orderingProps.paymentTimeout)
        orders.findAllByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, cutoff).forEach {
            log.info("Order {} cancelled: payment timeout", it.number)
            cancelInternal(it, "payment timeout")
        }
    }

    // ---------------------------------------------------------------- OrderProgress (kitchen)
    @Transactional
    override fun markPreparing(orderId: UUID) {
        val order = load(orderId)
        if (order.status == OrderStatus.PLACED) {
            order.status = OrderStatus.PREPARING
            statusChanged(order)
        }
    }

    @Transactional
    override fun markReady(orderId: UUID) {
        val order = load(orderId)
        if (order.status == OrderStatus.PLACED || order.status == OrderStatus.PREPARING) {
            order.status = OrderStatus.READY
            order.readyAt = clock.instant()
            statusChanged(order)
        }
    }

    @Transactional
    override fun reopen(orderId: UUID) {
        val order = load(orderId)
        if (order.status == OrderStatus.READY) {
            order.status = OrderStatus.PREPARING
            order.readyAt = null
            statusChanged(order)
        }
    }

    @Transactional
    override fun updateEstimatedReadyAt(orderId: UUID, estimatedReadyAt: java.time.Instant) {
        val order = load(orderId)
        if (order.status in setOf(OrderStatus.PLACED, OrderStatus.PREPARING)) {
            order.estimatedReadyAt = estimatedReadyAt
            statusChanged(order)
        }
    }

    // ---------------------------------------------------------------- OrderPayments
    override fun payableOrder(orderId: UUID, trackingToken: String?): PayableOrder {
        val order = get(orderId, trackingToken)
        if (order.status != OrderStatus.PENDING_PAYMENT || order.paymentStatus != PaymentStatus.UNPAID) {
            throw ConflictException("payment.order_not_payable")
        }
        return PayableOrder(order.id, order.number, order.total, order.currency)
    }

    @Transactional
    override fun markPaid(orderId: UUID) {
        val order = load(orderId)
        if (order.paymentStatus != PaymentStatus.UNPAID) return
        order.paymentStatus = PaymentStatus.PAID
        when (order.status) {
            OrderStatus.PENDING_PAYMENT -> place(order)
            // Paid after the order already expired: refund instead of cooking it.
            OrderStatus.CANCELLED -> events.publishEvent(OrderCancelled(order.id, true, order.cancelReason ?: "cancelled"))
            else -> Unit
        }
    }

    @Transactional
    override fun markRefunded(orderId: UUID) {
        load(orderId).paymentStatus = PaymentStatus.REFUNDED
    }

    // ---------------------------------------------------------------- internals
    private fun place(order: OrderEntity) {
        val now = clock.instant()
        order.status = OrderStatus.PLACED
        order.placedAt = now
        events.publishEvent(
            OrderPlaced(
                orderId = order.id,
                number = order.number,
                channel = order.channel.name,
                placedAt = now,
                pickupAt = order.pickupAt,
                notes = order.notes,
                lines = order.lines.map { l ->
                    PlacedLine(
                        productName = LocalizedText.fromMap(l.productName)!!,
                        stationId = l.stationId,
                        prepTimeSec = l.prepTimeSec,
                        quantity = l.quantity,
                        modifiers = l.modifiers.map { LocalizedText.fromMap(it.name)!! },
                        notes = l.notes,
                    )
                },
            ),
        )
        statusChanged(order)
    }

    private fun cancelInternal(order: OrderEntity, reason: String) {
        order.status = OrderStatus.CANCELLED
        order.cancelledAt = clock.instant()
        order.cancelReason = reason
        events.publishEvent(OrderCancelled(order.id, order.paymentStatus == PaymentStatus.PAID, reason))
        statusChanged(order)
    }

    private fun transition(order: OrderEntity, to: OrderStatus, vararg allowedFrom: OrderStatus) {
        if (order.status !in allowedFrom) throw ConflictException("order.invalid_transition", order.number, order.status, to)
        order.status = to
    }

    private fun statusChanged(order: OrderEntity) = events.publishEvent(order.toEvent())

    private fun OrderEntity.toEvent() =
        OrderStatusChanged(id, number, channel.name, status.name, estimatedReadyAt, clock.instant())

    private fun load(orderId: UUID) = orders.findByIdOrNull(orderId) ?: throw NotFoundException("Order")

    private fun canAccess(order: OrderEntity, trackingToken: String?): Boolean =
        Actor.current()?.isStaff == true ||
            (trackingToken != null && MessageDigest.isEqual(hash(trackingToken).toByteArray(), order.trackingTokenHash.toByteArray()))

    private fun businessDate(): LocalDate = LocalDate.now(clock.withZone(props.timezone))

    private fun newToken(): String =
        ByteArray(24).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun hash(raw: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()))
}
