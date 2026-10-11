package ro.amadya.tables.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import ro.amadya.shared.BaseEntity
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "floor_area")
class FloorAreaEntity(
    @JdbcTypeCode(SqlTypes.JSON) var name: Map<String, String>,
    @Column(name = "sort_order") var sortOrder: Int = 0,
    var active: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "dining_table")
class DiningTableEntity(
    @Column(name = "area_id") var areaId: UUID,
    var label: String,
    var seats: Int,
    @Column(name = "sort_order") var sortOrder: Int = 0,
    var active: Boolean = true,
) : BaseEntity()

enum class SessionStatus { OPEN, CLOSED, MERGED }

/** Guests seated at a table, from the moment they sit down until the bill is paid. */
@Entity
@Table(name = "table_session")
class TableSessionEntity(
    @Column(name = "table_id") var tableId: UUID,
    var guests: Int,
    @Column(name = "waiter_id") val waiterId: UUID?,
    @Column(name = "waiter_name") val waiterName: String?,
    @Column(name = "opened_at") val openedAt: Instant,
) : BaseEntity() {
    @Enumerated(EnumType.STRING) var status: SessionStatus = SessionStatus.OPEN
    @Column(name = "closed_at") var closedAt: Instant? = null
    @Column(name = "merged_into_id") var mergedIntoId: UUID? = null
}

interface FloorAreaRepository : JpaRepository<FloorAreaEntity, UUID> {
    fun findAllByOrderBySortOrderAsc(): List<FloorAreaEntity>
}

interface DiningTableRepository : JpaRepository<DiningTableEntity, UUID> {
    fun findAllByOrderBySortOrderAscLabelAsc(): List<DiningTableEntity>
    fun existsByLabelIgnoreCaseAndIdNot(label: String, id: UUID): Boolean
}

interface TableSessionRepository : JpaRepository<TableSessionEntity, UUID> {
    fun findAllByStatus(status: SessionStatus): List<TableSessionEntity>
    fun findByTableIdAndStatus(tableId: UUID, status: SessionStatus): TableSessionEntity?
}
