package ro.amadya.kitchen.internal

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
import org.springframework.data.jpa.repository.JpaRepository
import ro.amadya.shared.BaseEntity
import ro.amadya.shared.Ids
import java.time.Instant
import java.util.UUID

enum class TicketStatus { QUEUED, IN_PROGRESS, READY }

@Entity
@Table(name = "kitchen_ticket")
class KitchenTicketEntity(
    @Column(name = "order_id") val orderId: UUID,
    @Column(name = "order_number") val orderNumber: String,
    val channel: String,
    @Column(name = "station_id") val stationId: UUID,
    @Enumerated(EnumType.STRING) var status: TicketStatus = TicketStatus.QUEUED,
    @Column(name = "prep_time_sec") val prepTimeSec: Int,
    @Column(name = "queued_at") val queuedAt: Instant,
    @Column(name = "estimated_ready_at") var estimatedReadyAt: Instant,
    val notes: String? = null,
    @Column(name = "customer_name") val customerName: String? = null,
    @Column(name = "table_label") val tableLabel: String? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "ticket_id", nullable = false)
    @OrderBy("position ASC")
    val lines: MutableList<KitchenTicketLineEntity> = mutableListOf(),
) : BaseEntity() {
    @Column(name = "started_at") var startedAt: Instant? = null
    @Column(name = "ready_at") var readyAt: Instant? = null
}

@Entity
@Table(name = "kitchen_ticket_line")
class KitchenTicketLineEntity(
    @Id val id: UUID = Ids.newId(),
    val position: Int,
    @Column(name = "product_name") @JdbcTypeCode(SqlTypes.JSON) val productName: Map<String, String>,
    val quantity: Int,
    @JdbcTypeCode(SqlTypes.JSON) val modifiers: List<Map<String, String>>,
    val notes: String?,
)

interface KitchenTicketRepository : JpaRepository<KitchenTicketEntity, UUID> {
    fun existsByOrderId(orderId: UUID): Boolean
    fun findAllByOrderId(orderId: UUID): List<KitchenTicketEntity>
    fun countByStationIdAndStatusIn(stationId: UUID, statuses: Collection<TicketStatus>): Long
    fun findAllByStatusInOrReadyAtAfterOrderByQueuedAtAsc(statuses: Collection<TicketStatus>, readyAfter: Instant): List<KitchenTicketEntity>
}
