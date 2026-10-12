package ro.amadya.audit.internal

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.ActivityApi
import ro.amadya.contract.model.ActivityActor
import ro.amadya.contract.model.ActivityApp
import ro.amadya.contract.model.ActivityEntry
import ro.amadya.contract.model.ActivityFilters
import ro.amadya.contract.model.ActivityOutcome
import ro.amadya.contract.model.ActivityPage
import ro.amadya.contract.model.Role
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.Actor
import ro.amadya.shared.AmadyaProperties
import ro.amadya.shared.Ids
import ro.amadya.shared.Roles
import ro.amadya.shared.UnprocessableException
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

data class NewActivity(
    val at: Instant,
    val actorId: UUID?,
    val actorName: String,
    val actorRoles: List<String>,
    val app: String,
    val action: String,
    val method: String,
    val path: String,
    val status: Int,
    val targetId: String?,
    val target: String?,
    val detail: String?,
    val error: String?,
    val ip: String?,
)

/**
 * The activity log (ADR 0011): append-only, written once per staff write request, read by the admin.
 * An ADMIN sees everything; a MANAGER sees only the staff below them (waiters, kitchen, cashiers),
 * never other managers or admins, nor login attempts with unknown emails.
 */
@Service
@Transactional(readOnly = true)
class ActivityLogService(
    private val jdbc: JdbcClient,
    private val settings: RestaurantSettingsApi,
    private val props: AmadyaProperties,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** In its own transaction: the request's own work is already committed or rolled back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(a: NewActivity) {
        jdbc.sql(
            """INSERT INTO activity_log (id, at, actor_id, actor_name, actor_roles, app, action, method, path, status, target_id, target, detail, error, ip)
               VALUES (:id, :at, :actorId, :actorName, :actorRoles, :app, :action, :method, :path, :status, :targetId, :target, :detail, :error, :ip)""",
        )
            .param("id", Ids.newId()).param("at", a.at.atOffset(ZoneOffset.UTC)).param("actorId", a.actorId).param("actorName", a.actorName.take(200))
            .param("actorRoles", a.actorRoles.toTypedArray()).param("app", a.app).param("action", a.action).param("method", a.method)
            .param("path", a.path.take(500)).param("status", a.status).param("targetId", a.targetId?.take(100)).param("target", a.target?.take(200))
            .param("detail", a.detail?.take(1000)).param("error", a.error?.take(100)).param("ip", a.ip)
            .update()
    }

    fun list(
        actorId: UUID?, role: Role?, app: ActivityApp?, action: String?, outcome: ActivityOutcome?,
        from: LocalDate?, to: LocalDate?, before: UUID?, limit: Int,
    ): ActivityPage {
        if (from != null && to != null && to.isBefore(from)) throw UnprocessableException("activity.invalid_range")
        val where = mutableListOf(visibility())
        val params = mutableMapOf<String, Any>()
        actorId?.let { where += "actor_id = :actorId"; params["actorId"] = it }
        role?.let { where += ":role = ANY(actor_roles)"; params["role"] = it.value }
        app?.let { where += "app = :app"; params["app"] = it.value }
        action?.let { where += "action = :action"; params["action"] = it }
        when (outcome) {
            ActivityOutcome.OK -> where += "status < 400"
            ActivityOutcome.DENIED -> where += "status IN (401, 403)"
            ActivityOutcome.FAILED -> where += "status >= 400 AND status NOT IN (401, 403)"
            null -> {}
        }
        from?.let { where += "at >= :from"; params["from"] = it.atStartOfDay(props.timezone).toOffsetDateTime() }
        to?.let { where += "at < :to"; params["to"] = it.plusDays(1).atStartOfDay(props.timezone).toOffsetDateTime() }
        before?.let { where += "id < :before"; params["before"] = it }

        val rows = jdbc.sql("SELECT * FROM activity_log WHERE ${where.joinToString(" AND ")} ORDER BY id DESC LIMIT ${limit + 1}")
            .params(params).query { rs, _ -> rs.toEntry() }.list()
        val page = rows.take(limit)
        return ActivityPage(items = page, nextCursor = if (rows.size > limit) page.last().id else null)
    }

    fun filters(): ActivityFilters {
        val visible = visibility()
        val actors = jdbc.sql(
            """SELECT DISTINCT ON (actor_id) actor_id, actor_name, actor_roles FROM activity_log
               WHERE $visible AND actor_id IS NOT NULL ORDER BY actor_id, id DESC""",
        ).query { rs, _ -> ActivityActor(name = rs.getString("actor_name"), roles = rs.roles(), id = rs.getObject("actor_id", UUID::class.java)) }
            .list().sortedBy { it.name.lowercase() }
        val actions = jdbc.sql("SELECT DISTINCT action FROM activity_log WHERE $visible ORDER BY action").query { rs, _ -> rs.getString(1) }.list()
        return ActivityFilters(actors = actors, actions = actions)
    }

    /** Deletes what is older than the retention the admin chose (restaurant settings). */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    @Transactional
    fun purge() {
        val cutoff = clock.instant().minus(settings.activityRetentionDays().toLong(), ChronoUnit.DAYS)
        val deleted = jdbc.sql("DELETE FROM activity_log WHERE at < :cutoff").param("cutoff", cutoff.atOffset(ZoneOffset.UTC)).update()
        if (deleted > 0) logger.info("Activity log: deleted {} entries older than {}", deleted, cutoff)
    }

    private fun visibility(): String =
        if (Actor.current()?.hasAny(Roles.ADMIN) == true) "true"
        else "actor_id IS NOT NULL AND NOT (actor_roles && ARRAY['${Roles.ADMIN}', '${Roles.MANAGER}']::text[])"

    private fun ResultSet.roles(): List<Role> =
        (getArray("actor_roles").array as Array<*>).mapNotNull { r -> Role.entries.firstOrNull { it.value == r } }

    private fun ResultSet.toEntry(): ActivityEntry {
        val status = getInt("status")
        return ActivityEntry(
            id = getObject("id", UUID::class.java),
            at = getObject("at", java.time.OffsetDateTime::class.java),
            actor = ActivityActor(name = getString("actor_name"), roles = roles(), id = getObject("actor_id", UUID::class.java)),
            app = ActivityApp.forValue(getString("app")),
            action = getString("action"),
            method = getString("method"),
            path = getString("path"),
            status = status,
            outcome = when {
                status < 400 -> ActivityOutcome.OK
                status == 401 || status == 403 -> ActivityOutcome.DENIED
                else -> ActivityOutcome.FAILED
            },
            targetId = getString("target_id"),
            target = getString("target"),
            detail = getString("detail"),
            error = getString("error"),
            ip = getString("ip"),
        )
    }
}

@RestController
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
class ActivityController(private val log: ActivityLogService) : ActivityApi {

    override fun listActivity(
        actorId: UUID?, role: Role?, app: ActivityApp?, action: String?, outcome: ActivityOutcome?,
        from: LocalDate?, to: LocalDate?, before: UUID?, limit: Int,
    ): ResponseEntity<ActivityPage> = ResponseEntity.ok(log.list(actorId, role, app, action, outcome, from, to, before, limit))

    override fun getActivityFilters(): ResponseEntity<ActivityFilters> = ResponseEntity.ok(log.filters())
}
