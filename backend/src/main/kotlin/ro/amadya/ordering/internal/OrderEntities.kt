package ro.amadya.ordering.internal

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ro.amadya.shared.BaseEntity
import ro.amadya.shared.Ids
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class OrderChannel { TAKEAWAY, DINE_IN, COUNTER }

enum class OrderStatus { PENDING_PAYMENT, PLACED, PREPARING, READY, COMPLETED, CANCELLED }

enum class PaymentStatus { UNPAID, PAID, REFUNDED }

@Entity
@Table(name = "customer_order")
class OrderEntity(
    val number: String,
    @Column(name = "business_date") val businessDate: LocalDate,
    @Enumerated(EnumType.STRING) val channel: OrderChannel,
    @Enumerated(EnumType.STRING) var status: OrderStatus,
    @Column(name = "customer_name") var customerName: String? = null,
    @Column(name = "customer_phone") var customerPhone: String? = null,
    @Column(name = "customer_email") var customerEmail: String? = null,
    @Column(name = "customer_user_id") var customerUserId: UUID? = null,
    @Column(name = "created_by_user_id") val createdByUserId: UUID? = null,
    @Column(name = "table_session_id") val tableSessionId: UUID? = null,
    @Column(name = "pickup_at") val pickupAt: Instant? = null,
    val notes: String? = null,
    val locale: String,
    val currency: String,
    val total: BigDecimal,
    @Column(name = "vat_total") val vatTotal: BigDecimal,
    @Column(name = "tracking_token_hash") val trackingTokenHash: String,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("position ASC")
    val lines: MutableList<OrderLineEntity> = mutableListOf(),
) : BaseEntity() {
    @Enumerated(EnumType.STRING) @Column(name = "payment_status") var paymentStatus: PaymentStatus = PaymentStatus.UNPAID
    @Column(name = "estimated_ready_at") var estimatedReadyAt: Instant? = null
    @Column(name = "placed_at") var placedAt: Instant? = null
    @Column(name = "ready_at") var readyAt: Instant? = null
    @Column(name = "completed_at") var completedAt: Instant? = null
    @Column(name = "cancelled_at") var cancelledAt: Instant? = null
    @Column(name = "cancel_reason") var cancelReason: String? = null
}

@Entity
@Table(name = "order_line")
class OrderLineEntity(
    @Id val id: UUID = Ids.newId(),
    val position: Int,
    @Column(name = "product_id") val productId: UUID,
    @Column(name = "product_name") @JdbcTypeCode(SqlTypes.JSON) val productName: Map<String, String>,
    @Column(name = "station_id") val stationId: UUID?,
    @Column(name = "prep_time_sec") val prepTimeSec: Int,
    val quantity: Int,
    @Column(name = "unit_price") val unitPrice: BigDecimal,
    @Column(name = "vat_percent") val vatPercent: BigDecimal,
    val total: BigDecimal,
    @Column(name = "vat_amount") val vatAmount: BigDecimal,
    val notes: String?,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "line_id", nullable = false)
    val modifiers: MutableList<OrderLineModifierEntity> = mutableListOf(),
)

@Entity
@Table(name = "order_line_modifier")
class OrderLineModifierEntity(
    @Id val id: UUID = Ids.newId(),
    @Column(name = "option_id") val optionId: UUID,
    @JdbcTypeCode(SqlTypes.JSON) val name: Map<String, String>,
    @Column(name = "price_delta") val priceDelta: BigDecimal,
)
