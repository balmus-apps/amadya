package ro.amadya.ordering.internal

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.OrderingApi
import ro.amadya.contract.model.CancelOrderRequest
import ro.amadya.contract.model.CreateOrderRequest
import ro.amadya.contract.model.CustomerContact
import ro.amadya.contract.model.Order
import ro.amadya.contract.model.OrderLine
import ro.amadya.contract.model.QueueBoard
import ro.amadya.shared.localized
import ro.amadya.shared.toMoney
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import ro.amadya.contract.model.OrderChannel as OrderChannelDto
import ro.amadya.contract.model.OrderStatus as OrderStatusDto
import ro.amadya.contract.model.PaymentStatus as PaymentStatusDto

/** Transactional so DTO mapping can read lazy order lines (open-in-view is off). */
@RestController
@Transactional
class OrderController(private val service: OrderService) : OrderingApi {

    override fun createOrder(createOrderRequest: CreateOrderRequest, acceptLanguage: String?): ResponseEntity<Order> {
        val locale = LocaleContextHolder.getLocale()
        val created = service.create(createOrderRequest, locale)
        return ResponseEntity.status(HttpStatus.CREATED).body(created.order.toDto(locale).copy(trackingToken = created.trackingToken))
    }

    override fun getOrder(orderId: UUID, token: String?, acceptLanguage: String?): ResponseEntity<Order> =
        ResponseEntity.ok(service.get(orderId, token).toDto(LocaleContextHolder.getLocale()))

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WAITER','KITCHEN','CASHIER')")
    override fun listOrders(status: List<OrderStatusDto>?, channel: OrderChannelDto?, limit: Int): ResponseEntity<List<Order>> {
        val locale = LocaleContextHolder.getLocale()
        val orders = service.list(
            status.orEmpty().map { OrderStatus.valueOf(it.value) },
            channel?.let { OrderChannel.valueOf(it.value) },
            limit,
        )
        return ResponseEntity.ok(orders.map { it.toDto(locale) })
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WAITER','CASHIER')")
    override fun completeOrder(orderId: UUID): ResponseEntity<Order> =
        ResponseEntity.ok(service.complete(orderId).toDto(LocaleContextHolder.getLocale()))

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WAITER','CASHIER')")
    override fun cancelOrder(orderId: UUID, cancelOrderRequest: CancelOrderRequest): ResponseEntity<Order> =
        ResponseEntity.ok(service.cancel(orderId, cancelOrderRequest.reason).toDto(LocaleContextHolder.getLocale()))

    override fun getQueueBoard(): ResponseEntity<QueueBoard> =
        service.queueBoard().let { ResponseEntity.ok(QueueBoard(it.preparing, it.ready)) }
}

internal fun Instant.toOffset(): OffsetDateTime = atOffset(ZoneOffset.UTC)

internal fun OrderEntity.toDto(locale: Locale) = Order(
    id = id,
    number = number,
    channel = OrderChannelDto.forValue(channel.name),
    status = OrderStatusDto.forValue(status.name),
    paymentStatus = PaymentStatusDto.forValue(paymentStatus.name),
    lines = lines.map { l ->
        OrderLine(
            id = l.id,
            productId = l.productId,
            productName = l.productName.localized(locale),
            quantity = l.quantity,
            unitPrice = l.unitPrice.toMoney(currency),
            modifiers = l.modifiers.map { it.name.localized(locale) },
            total = l.total.toMoney(currency),
            notes = l.notes,
        )
    },
    total = total.toMoney(currency),
    vatTotal = vatTotal.toMoney(currency),
    createdAt = createdAt.toOffset(),
    customer = customerName?.let { CustomerContact(it, customerPhone.orEmpty(), customerEmail) },
    tableSessionId = tableSessionId,
    pickupAt = pickupAt?.toOffset(),
    estimatedReadyAt = estimatedReadyAt?.toOffset(),
    notes = notes,
    cancelReason = cancelReason,
)
