package ro.amadya.payments.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import ro.amadya.shared.BaseEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class PaymentMethod { CASH, CARD_POS, ONLINE }

enum class PaymentState { PENDING, CAPTURED, FAILED, REFUNDED }

@Entity
@Table(name = "payment")
class PaymentEntity(
    @Column(name = "order_id") val orderId: UUID,
    @Enumerated(EnumType.STRING) val method: PaymentMethod,
    val provider: String,
    val amount: BigDecimal,
    val currency: String,
    @Enumerated(EnumType.STRING) var status: PaymentState = PaymentState.PENDING,
    @Column(name = "provider_ref") var providerRef: String? = null,
) : BaseEntity() {
    @Column(name = "captured_at") var capturedAt: Instant? = null
    @Column(name = "refunded_at") var refundedAt: Instant? = null
    var failure: String? = null
}

interface PaymentRepository : JpaRepository<PaymentEntity, UUID> {
    fun findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId: UUID, status: PaymentState): PaymentEntity?
    fun findByProviderAndProviderRef(provider: String, providerRef: String): PaymentEntity?
}
