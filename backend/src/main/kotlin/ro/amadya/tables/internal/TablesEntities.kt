package ro.amadya.tables.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import ro.amadya.shared.BaseEntity
import ro.amadya.shared.Ids
import java.math.BigDecimal
import java.security.SecureRandom
import java.time.Instant
import java.util.HexFormat
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
    /** Code printed in the table's QR (https://<domain>/t/<code>): random, never the table number. */
    @Column(name = "qr_code") var qrCode: String = newQrCode(),
) : BaseEntity()

private val random = SecureRandom()

fun newQrCode(): String = HexFormat.of().formatHex(ByteArray(16).also(random::nextBytes))

enum class SessionStatus { OPEN, CLOSED, MERGED }

enum class SessionOrigin { WAITER, GUEST }

/** Guests seated at a table, from the moment they sit down until the bill is paid. */
@Entity
@Table(name = "table_session")
class TableSessionEntity(
    @Column(name = "table_id") var tableId: UUID,
    var guests: Int,
    @Column(name = "waiter_id") var waiterId: UUID?,
    @Column(name = "waiter_name") var waiterName: String?,
    @Column(name = "opened_at") val openedAt: Instant,
    @Enumerated(EnumType.STRING) val origin: SessionOrigin = SessionOrigin.WAITER,
    /** Sessions started by guests from the QR code wait for a waiter, or for [confirmDeadline]. */
    @Column(name = "confirmed_at") var confirmedAt: Instant? = openedAt,
    @Column(name = "confirm_deadline") var confirmDeadline: Instant? = null,
) : BaseEntity() {
    @Enumerated(EnumType.STRING) var status: SessionStatus = SessionStatus.OPEN
    @Column(name = "closed_at") var closedAt: Instant? = null
    @Column(name = "merged_into_id") var mergedIntoId: UUID? = null

    val awaitingConfirmation get() = confirmedAt == null
}

enum class GuestRole { OWNER, MEMBER }

enum class GuestStatus { PENDING, ACTIVE, REJECTED, REMOVED }

/** A guest who scanned the table's QR code: a first name and a device token valid for this session only. */
@Entity
@Table(name = "table_guest")
class TableGuestEntity(
    @Column(name = "session_id") var sessionId: UUID,
    val name: String,
    @Enumerated(EnumType.STRING) var role: GuestRole,
    @Enumerated(EnumType.STRING) var status: GuestStatus,
    @Column(name = "token_hash") val tokenHash: String,
    @Column(name = "requested_at") val requestedAt: Instant,
) : BaseEntity() {
    @Column(name = "decided_at") var decidedAt: Instant? = null
}

enum class PendingRoundStatus { PENDING, CONFIRMED, REJECTED }

/** One line of a guest round, priced when it was sent; names are kept for the waiter and the log. */
data class PendingLine(
    val productId: UUID,
    val quantity: Int,
    val optionIds: List<UUID>,
    val notes: String?,
    val name: Map<String, String>,
    val modifiers: List<Map<String, String>>,
    val total: BigDecimal,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "productId" to productId.toString(), "quantity" to quantity, "optionIds" to optionIds.map { it.toString() },
        "notes" to notes, "name" to name, "modifiers" to modifiers, "total" to total.toPlainString(),
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(map: Map<String, Any?>) = PendingLine(
            productId = UUID.fromString(map["productId"] as String),
            quantity = (map["quantity"] as Number).toInt(),
            optionIds = (map["optionIds"] as List<String>).map(UUID::fromString),
            notes = map["notes"] as String?,
            name = map["name"] as Map<String, String>,
            modifiers = map["modifiers"] as List<Map<String, String>>,
            total = BigDecimal(map["total"].toString()),
        )
    }
}

/** A round ordered by a guest, waiting for the waiter's OK; it becomes an order once confirmed. */
@Entity
@Table(name = "pending_round")
class PendingRoundEntity(
    @Column(name = "session_id") var sessionId: UUID,
    @Column(name = "guest_id") val guestId: UUID,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "lines") val linesJson: List<Map<String, Any?>>,
    val notes: String?,
    val total: BigDecimal,
    val deadline: Instant,
) : BaseEntity() {
    @Enumerated(EnumType.STRING) var status: PendingRoundStatus = PendingRoundStatus.PENDING
    @Column(name = "decided_at") var decidedAt: Instant? = null
    @Column(name = "decided_by") var decidedBy: String? = null
    var reason: String? = null
    @Column(name = "order_id") var orderId: UUID? = null

    val lines: List<PendingLine> get() = linesJson.map(PendingLine::fromJson)
}

/** Who paid an online payment started at the table. */
@Entity
@Table(name = "guest_payment")
class GuestPaymentEntity(
    @Id @Column(name = "payment_id") val paymentId: UUID,
    @Column(name = "session_id") var sessionId: UUID,
    @Column(name = "guest_id") val guestId: UUID,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
)

enum class ActivityKind { JOINED, REMOVED, ORDERED, ROUND_CONFIRMED, ROUND_REJECTED, TABLE_CONFIRMED, PAID }

enum class ActorKind { GUEST, WAITER, SYSTEM }

/** One line of what happened at the table. [details] holds items (localized names), amount, method, reason. */
@Entity
@Table(name = "table_activity")
class TableActivityEntity(
    @Column(name = "session_id") var sessionId: UUID,
    val at: Instant,
    @Enumerated(EnumType.STRING) val kind: ActivityKind,
    val actor: String?,
    @Enumerated(EnumType.STRING) @Column(name = "actor_kind") val actorKind: ActorKind,
    @JdbcTypeCode(SqlTypes.JSON) val details: Map<String, Any?> = emptyMap(),
    @Id val id: UUID = Ids.newId(),
)

interface FloorAreaRepository : JpaRepository<FloorAreaEntity, UUID> {
    fun findAllByOrderBySortOrderAsc(): List<FloorAreaEntity>
}

interface DiningTableRepository : JpaRepository<DiningTableEntity, UUID> {
    fun findAllByOrderBySortOrderAscLabelAsc(): List<DiningTableEntity>
    fun existsByLabelIgnoreCaseAndIdNot(label: String, id: UUID): Boolean
    fun findByQrCode(qrCode: String): DiningTableEntity?
}

interface TableSessionRepository : JpaRepository<TableSessionEntity, UUID> {
    fun findAllByStatus(status: SessionStatus): List<TableSessionEntity>
    fun findByTableIdAndStatus(tableId: UUID, status: SessionStatus): TableSessionEntity?
    fun findAllByStatusAndConfirmedAtIsNullAndConfirmDeadlineBefore(status: SessionStatus, before: Instant): List<TableSessionEntity>
}

interface TableGuestRepository : JpaRepository<TableGuestEntity, UUID> {
    fun findByTokenHash(tokenHash: String): TableGuestEntity?
    fun findAllBySessionIdInOrderByRequestedAtAsc(sessionIds: Collection<UUID>): List<TableGuestEntity>
    fun findAllByStatusAndRequestedAtBefore(status: GuestStatus, before: Instant): List<TableGuestEntity>
}

interface PendingRoundRepository : JpaRepository<PendingRoundEntity, UUID> {
    fun findAllBySessionIdInOrderByCreatedAtAsc(sessionIds: Collection<UUID>): List<PendingRoundEntity>
    fun findAllByStatusAndDeadlineBefore(status: PendingRoundStatus, before: Instant): List<PendingRoundEntity>
    fun countByGuestIdAndStatus(guestId: UUID, status: PendingRoundStatus): Long
}

interface GuestPaymentRepository : JpaRepository<GuestPaymentEntity, UUID> {
    fun findAllByGuestId(guestId: UUID): List<GuestPaymentEntity>
    fun findAllBySessionId(sessionId: UUID): List<GuestPaymentEntity>
}

interface TableActivityRepository : JpaRepository<TableActivityEntity, UUID> {
    fun findAllBySessionIdInOrderByAtAsc(sessionIds: Collection<UUID>): List<TableActivityEntity>
    fun findAllBySessionId(sessionId: UUID): List<TableActivityEntity>
}
