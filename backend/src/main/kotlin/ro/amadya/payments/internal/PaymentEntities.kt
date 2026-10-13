package ro.amadya.payments.internal

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import ro.amadya.shared.BaseEntity
import ro.amadya.shared.Ids
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class PaymentMethod { CASH, CARD_POS, ONLINE }

enum class PaymentState { PENDING, CAPTURED, FAILED, REFUNDED, VOIDED }

@Entity
@Table(name = "payment")
class PaymentEntity(
    /** The order paid (online, or at pickup); null for payments taken at a table. */
    @Column(name = "order_id") val orderId: UUID?,
    @Enumerated(EnumType.STRING) val method: PaymentMethod,
    val provider: String,
    val amount: BigDecimal,
    val currency: String,
    @Enumerated(EnumType.STRING) var status: PaymentState = PaymentState.PENDING,
    @Column(name = "provider_ref") var providerRef: String? = null,
    @Column(name = "table_session_id") val tableSessionId: UUID? = null,
    val tendered: BigDecimal? = null,
    @Column(name = "recorded_by") val recordedBy: UUID? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "payment_id", nullable = false)
    val allocations: MutableList<PaymentAllocationEntity> = mutableListOf(),
) : BaseEntity() {
    @Column(name = "captured_at") var capturedAt: Instant? = null
    @Column(name = "refunded_at") var refundedAt: Instant? = null
    @Column(name = "voided_at") var voidedAt: Instant? = null
    var failure: String? = null
}

@Entity
@Table(name = "payment_allocation")
class PaymentAllocationEntity(
    @Id val id: UUID = Ids.newId(),
    @Column(name = "order_line_id") val orderLineId: UUID,
    val quantity: Int,
    val amount: BigDecimal,
)

interface PaymentRepository : JpaRepository<PaymentEntity, UUID> {
    fun findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId: UUID, status: PaymentState): PaymentEntity?
    fun findByProviderAndProviderRef(provider: String, providerRef: String): PaymentEntity?
    fun findAllByTableSessionIdInOrderByCreatedAtAsc(sessionIds: Collection<UUID>): List<PaymentEntity>
    fun findAllByStatusAndTableSessionIdIsNotNullAndCreatedAtBefore(status: PaymentState, before: java.time.Instant): List<PaymentEntity>
}
