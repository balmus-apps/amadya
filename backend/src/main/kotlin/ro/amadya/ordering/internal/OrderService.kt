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
import ro.amadya.ordering.OrderSettledInPerson
import ro.amadya.ordering.OrderStatusChanged
import ro.amadya.ordering.PayableOrder
import ro.amadya.ordering.PlacedLine
import ro.amadya.ordering.QueueBoardView
import ro.amadya.ordering.ReadyOrder
import ro.amadya.ordering.RoundLine
import ro.amadya.ordering.SessionOrder
import ro.amadya.ordering.SessionOrderLine
import ro.amadya.ordering.TableOrders
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.Activity
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
data class OrderingProperties(
    val paymentTimeout: Duration = Duration.ofMinutes(15),
    /** How long a ready takeaway / counter order stays on the queue display when nobody marks it picked up. */
    val queueReadyVisible: Duration = Duration.ofMinutes(20),
)

/** How staff took the payment of a takeaway / counter order: at the counter when ordering, or at pickup. */
data class PickupPayment(val method: String, val tendered: BigDecimal?) {
    companion object {
        const val CASH = "CASH"
    }
}

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
) : OrderProgress, OrderPayments, OrderQueries, TableOrders {

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
            OrderChannel.COUNTER -> if (!staff) throw ForbiddenException("order.staff_only", channel.name)
            // Rounds belong to a table session, which only the tables module opens and checks.
            OrderChannel.DINE_IN -> throw UnprocessableException("order.dine_in_via_tables")
        }

        // Staff may take the money of a counter order when they create it (the cashier app).
        val payment = req.payment?.let { PickupPayment(it.method.value, it.tendered?.let(Money::of)) }
        if (payment != null && !(staff && channel == OrderChannel.COUNTER)) throw UnprocessableException("order.payment_counter_only")

        // Guests pay online first when online payments are on; otherwise their order waits for the cashier to confirm
        // it (unless the restaurant turned confirmation off). Staff-created orders go straight to the kitchen.
        val customerTakeaway = channel == OrderChannel.TAKEAWAY && !staff
        val status = when {
            customerTakeaway && features.onlinePayments -> OrderStatus.PENDING_PAYMENT
            customerTakeaway && settings.takeawayConfirmMinutes() > 0 -> OrderStatus.AWAITING_CONFIRMATION
            else -> OrderStatus.PLACED
        }
        return createOrder(
            channel = channel,
            status = status,
            payment = payment,
            lines = req.lines.map { LineSelection(it.productId, it.quantity, it.modifierOptionIds.orEmpty(), it.notes) },
            locale = locale,
            notes = req.notes,
            pickupAt = req.pickupAt?.toInstant(),
            customerName = req.customer?.name?.trim(),
            customerPhone = req.customer?.phone,
            customerEmail = req.customer?.email,
            customerUserId = actor?.takeIf { Roles.CUSTOMER in it.roles }?.userId,
        )
    }

    // ---------------------------------------------------------------- TableOrders (tables module)
    @Transactional
    override fun placeRound(sessionId: UUID, tableLabel: String, lines: List<RoundLine>, notes: String?, locale: Locale): UUID =
        createOrder(
            channel = OrderChannel.DINE_IN,
            status = OrderStatus.PLACED,
            lines = lines.map { LineSelection(it.productId, it.quantity, it.modifierOptionIds, it.notes) },
            locale = locale,
            notes = notes,
            tableSessionId = sessionId,
            tableLabel = tableLabel,
        ).order.id

    override fun sessionOrders(sessionIds: Collection<UUID>): List<SessionOrder> =
        if (sessionIds.isEmpty()) emptyList() else orders.findAllByTableSessionIdInOrderByCreatedAtAsc(sessionIds).map { o ->
            SessionOrder(
                orderId = o.id,
                sessionId = o.tableSessionId!!,
                number = o.number,
                status = o.status.name,
                placedAt = o.placedAt,
                readyAt = o.readyAt,
                total = o.total,
                currency = o.currency,
                lines = o.lines.map { l ->
                    SessionOrderLine(
                        lineId = l.id,
                        productName = LocalizedText.fromMap(l.productName)!!,
                        modifiers = l.modifiers.map { LocalizedText.fromMap(it.name)!! },
                        quantity = l.quantity,
                        unitPrice = l.unitPrice,
                        total = l.total,
                        notes = l.notes,
                    )
                },
            )
        }

    @Transactional
    override fun settle(sessionId: UUID) {
        val now = clock.instant()
        orders.findAllByTableSessionIdInOrderByCreatedAtAsc(listOf(sessionId)).filter { it.status != OrderStatus.CANCELLED }.forEach { order ->
            order.paymentStatus = PaymentStatus.PAID
            // Paying closes the table: anything not yet marked served is considered served. The kitchen can still
            // bump its tickets; that no longer moves a completed order.
            if (order.status != OrderStatus.COMPLETED) {
                order.status = OrderStatus.COMPLETED
                order.completedAt = now
                statusChanged(order)
            }
        }
    }

    @Transactional
    override fun moveToSession(fromSessionId: UUID, toSessionId: UUID) {
        orders.findAllByTableSessionIdInOrderByCreatedAtAsc(listOf(fromSessionId)).forEach {
            it.tableSessionId = toSessionId
            statusChanged(it)
        }
    }

    private fun createOrder(
        channel: OrderChannel,
        status: OrderStatus,
        lines: List<LineSelection>,
        locale: Locale,
        notes: String?,
        pickupAt: java.time.Instant? = null,
        customerName: String? = null,
        customerPhone: String? = null,
        customerEmail: String? = null,
        customerUserId: UUID? = null,
        tableSessionId: UUID? = null,
        tableLabel: String? = null,
        payment: PickupPayment? = null,
    ): CreatedOrder {
        val actor = Actor.current()
        val priced = catalog.priceLines(lines)
        val orderLines = priced.mapIndexed { index, p ->
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
        val token = newToken()
        val order = OrderEntity(
            number = numbers.next(businessDate(), settings.orderNumberPrefix()),
            businessDate = businessDate(),
            channel = channel,
            status = status,
            customerName = customerName,
            customerPhone = customerPhone,
            customerEmail = customerEmail,
            customerUserId = customerUserId,
            createdByUserId = actor?.takeIf { it.isStaff }?.userId,
            tableSessionId = tableSessionId,
            pickupAt = pickupAt,
            notes = notes?.trim()?.ifBlank { null },
            locale = locale.language.takeIf { it == "en" } ?: "ro",
            currency = settings.currency(),
            total = orderLines.fold(BigDecimal.ZERO) { acc, l -> acc + l.total },
            vatTotal = orderLines.fold(BigDecimal.ZERO) { acc, l -> acc + l.vatAmount },
            trackingTokenHash = hash(token),
            lines = orderLines.toMutableList(),
        )
        if (payment != null) {
            checkPayment(order, payment)
            order.paymentStatus = PaymentStatus.PAID
        }
        orders.save(order)
        if (payment != null) settledInPerson(order, payment)
        if (order.status == OrderStatus.PLACED) place(order, tableLabel) else statusChanged(order)
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
        val shownSince = clock.instant().minus(orderingProps.queueReadyVisible)
        return QueueBoardView(
            preparing = visible.filter { it.status != OrderStatus.READY }.map { it.number },
            ready = visible.filter { it.status == OrderStatus.READY && it.readyAt != null && it.readyAt!!.isAfter(shownSince) }
                .sortedByDescending { it.readyAt }
                .map { ReadyOrder(it.number, it.readyAt!!) },
            readyVisible = orderingProps.queueReadyVisible,
        )
    }

    // ---------------------------------------------------------------- staff actions
    /**
     * Hands the order over. An unpaid takeaway or counter order is paid at pickup, so [payment] is then required;
     * dine-in rounds are settled on the table bill and paid orders need nothing.
     */
    @Transactional
    fun complete(orderId: UUID, payment: PickupPayment? = null): OrderEntity {
        val order = load(orderId)
        val settle = order.channel != OrderChannel.DINE_IN && order.paymentStatus == PaymentStatus.UNPAID
        if (settle) checkPayment(order, payment ?: throw UnprocessableException("order.payment_required", order.number))
        transition(order, OrderStatus.COMPLETED, OrderStatus.READY)
        order.completedAt = clock.instant()
        if (settle) {
            order.paymentStatus = PaymentStatus.PAID
            settledInPerson(order, payment!!)
        }
        statusChanged(order)
        return order
    }

    /** A customer's order paid at pickup was checked by the cashier: send it to the kitchen. */
    @Transactional
    fun confirm(orderId: UUID): OrderEntity {
        val order = load(orderId)
        Activity.describe(target = order.number)
        transition(order, OrderStatus.PLACED, OrderStatus.AWAITING_CONFIRMATION)
        place(order)
        return order
    }

    /** The cashier refuses a waiting order; the customer sees the reason on the order page. */
    @Transactional
    fun refuse(orderId: UUID, reason: String): OrderEntity {
        val order = load(orderId)
        Activity.describe(target = order.number, detail = reason.trim().ifBlank { null })
        transition(order, OrderStatus.CANCELLED, OrderStatus.AWAITING_CONFIRMATION)
        cancelInternal(order, reason.trim())
        return order
    }

    @Transactional
    fun cancel(orderId: UUID, reason: String): OrderEntity {
        val order = load(orderId)
        Activity.describe(target = order.number, detail = reason.trim().ifBlank { null })
        if (order.status in setOf(OrderStatus.PREPARING, OrderStatus.READY) && Actor.current()?.hasAny(Roles.ADMIN, Roles.MANAGER) != true) {
            throw ForbiddenException("order.cancel_requires_manager")
        }
        transition(
            order, OrderStatus.CANCELLED,
            OrderStatus.PENDING_PAYMENT, OrderStatus.AWAITING_CONFIRMATION, OrderStatus.PLACED, OrderStatus.PREPARING, OrderStatus.READY,
        )
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

    /**
     * Orders nobody confirmed in time go to the kitchen anyway, so a busy counter never loses one. The setting is read on
     * every run: lowering it (or 0, confirmation off) also releases orders already waiting.
     */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    @Transactional
    fun confirmWaitingOrders() {
        val cutoff = clock.instant().minus(Duration.ofMinutes(settings.takeawayConfirmMinutes().toLong()))
        orders.findAllByStatusAndCreatedAtBefore(OrderStatus.AWAITING_CONFIRMATION, cutoff).forEach {
            log.info("Order {} confirmed automatically", it.number)
            place(it)
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
    private fun place(order: OrderEntity, tableLabel: String? = null) {
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
                customerName = order.customerName,
                tableLabel = tableLabel,
                lines = order.lines.map { l ->
                    PlacedLine(
                        productId = l.productId,
                        modifierOptionIds = l.modifiers.map { it.optionId },
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

    /** Same rules at the counter (paid when ordering) and at pickup: cash handed over must cover the total. */
    private fun checkPayment(order: OrderEntity, payment: PickupPayment) {
        if (payment.tendered != null && payment.method != PickupPayment.CASH) throw UnprocessableException("order.tendered_cash_only")
        if (payment.tendered != null && payment.tendered < order.total) throw UnprocessableException("order.tendered_too_low", order.total.toPlainString())
    }

    private fun settledInPerson(order: OrderEntity, payment: PickupPayment) = events.publishEvent(
        OrderSettledInPerson(order.id, payment.method, order.total, order.currency, payment.tendered, Actor.current()?.userId),
    )

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
        OrderStatusChanged(id, number, channel.name, status.name, estimatedReadyAt, clock.instant(), tableSessionId)

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
