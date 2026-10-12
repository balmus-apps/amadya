package ro.amadya

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import ro.amadya.audit.internal.ActivityLogService
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The activity log: what staff do in any app, seen by the admin (everything) and managers (the staff below them). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class)
@ActiveProfiles("test")
class ActivityLogIntegrationTests {

    @LocalServerPort
    private var port: Int = 0
    private lateinit var api: ApiClient

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var activity: ActivityLogService

    @BeforeEach
    fun setUp() {
        api = ApiClient(port)
    }

    /** Inactive and last, so the categories created here never show on the menu other tests read. */
    private fun hidden(name: String) = mapOf("name" to mapOf("ro" to name), "active" to false, "sortOrder" to 9999)

    private fun staff(admin: String, role: String): Pair<String, String> {
        val user = api.post("/api/v1/admin/users", mapOf("email" to "${role.lowercase()}-${UUID.randomUUID()}@test.local", "name" to "$role ${UUID.randomUUID().toString().take(4)}", "password" to "Password123!", "roles" to listOf(role)), admin)
        assertEquals(201, user.status, user.body.toString())
        return user.text("id") to api.login(user.text("email"), "Password123!")
    }

    @Test
    fun `admin sees what a manager did, with the app, the target and what changed`() {
        val admin = api.login("admin@test.local", "Admin12345!")
        val (managerId, manager) = staff(admin, "MANAGER")
        val fromAdmin = mapOf("X-Amadya-App" to "admin")

        val product = api.post(
            "/api/v1/admin/products",
            mapOf("categoryId" to api.post("/api/v1/admin/categories", hidden("Log"), admin).text("id"), "name" to mapOf("ro" to "Ciorbă de test"), "price" to "20.00", "vatRateId" to "0190a000-0000-7000-8000-000000000002"),
            admin,
        )
        val request = mapOf("categoryId" to product.text("categoryId"), "name" to mapOf("ro" to "Ciorbă de test"), "price" to "23.50", "vatRateId" to "0190a000-0000-7000-8000-000000000002")
        assertEquals(200, api.put("/api/v1/admin/products/${product.text("id")}", request, manager, fromAdmin).status)
        assertEquals(403, api.put("/api/v1/admin/settings", api.get("/api/v1/admin/settings", admin).body, manager, fromAdmin).status)

        val log = api.get("/api/v1/admin/activity?actorId=$managerId", admin)
        assertEquals(200, log.status, log.body.toString())
        val (denied, updated, login) = log["items"].toList().also { assertEquals(3, it.size) }
        assertEquals("login", login.path("action").asString())
        assertEquals("updateRestaurantSettings", denied.path("action").asString())
        assertEquals("DENIED", denied.path("outcome").asString())
        assertEquals("error.forbidden", denied.path("error").asString())
        assertEquals("updateProduct", updated.path("action").asString())
        assertEquals("OK", updated.path("outcome").asString())
        assertEquals("ADMIN", updated.path("app").asString())
        assertEquals("Ciorbă de test", updated.path("target").asString())
        assertEquals("Price 20.00 → 23.50", updated.path("detail").asString())
        assertEquals(product.text("id"), updated.path("targetId").asString())
        assertEquals(listOf("MANAGER"), updated.path("actor").path("roles").toList().map { it.asString() })

        val filters = api.get("/api/v1/admin/activity/filters", admin)
        assertTrue(filters["actors"].any { it.path("id").asString() == managerId })
        assertTrue(filters["actions"].any { it.asString() == "updateProduct" })
        assertEquals(1, api.get("/api/v1/admin/activity?actorId=$managerId&outcome=DENIED", admin)["items"].size())
    }

    @Test
    fun `logins are logged, and failed ones too, but token refreshes are not`() {
        val admin = api.login("admin@test.local", "Admin12345!")
        val (waiterId, _) = staff(admin, "WAITER")
        val unknown = "nobody-${UUID.randomUUID()}@test.local"
        assertEquals(401, api.post("/api/v1/auth/login", mapOf("email" to unknown, "password" to "guess")).status)

        val logins = api.get("/api/v1/admin/activity?action=login&limit=200", admin)["items"].toList()
        assertTrue(logins.any { it.path("actor").path("id").asString() == waiterId && it.path("outcome").asString() == "OK" })
        val failed = logins.single { it.path("actor").path("name").asString() == unknown }
        assertEquals("DENIED", failed.path("outcome").asString())
        assertEquals("auth.invalid_credentials", failed.path("error").asString())
        assertTrue(failed.path("actor").path("id").let { it.isMissingNode || it.isNull })
        assertTrue(api.get("/api/v1/admin/activity?action=refreshToken", admin)["items"].isEmpty)
    }

    @Test
    fun `a manager sees only the staff below them, and other staff cannot read the log`() {
        val admin = api.login("admin@test.local", "Admin12345!")
        val (cashierId, cashier) = staff(admin, "CASHIER")
        val (managerId, manager) = staff(admin, "MANAGER")
        val order = api.post("/api/v1/orders", mapOf("channel" to "COUNTER", "lines" to listOf(mapOf("productId" to "0190a000-0000-7000-8000-000000000452", "quantity" to 1))), cashier)
        assertEquals(201, order.status, order.body.toString())
        api.post("/api/v1/admin/categories", hidden("De la manager"), manager)

        val seen = api.get("/api/v1/admin/activity?limit=200", manager)["items"].toList()
        val created = seen.single { it.path("actor").path("id").asString() == cashierId && it.path("action").asString() == "createOrder" }
        assertEquals(order.text("number"), created.path("target").asString())
        assertEquals("OTHER", created.path("app").asString())
        assertFalse(seen.any { it.path("actor").path("roles").any { r -> r.asString() in setOf("ADMIN", "MANAGER") } })
        assertFalse(api.get("/api/v1/admin/activity/filters", manager)["actors"].any { it.path("id").asString() == managerId })

        assertEquals(403, api.get("/api/v1/admin/activity", cashier).status)
        assertEquals(422, api.get("/api/v1/admin/activity?from=2026-10-10&to=2026-10-09", admin).status)
    }

    @Test
    fun `pages follow the cursor and old entries go after the retention period`() {
        val admin = api.login("admin@test.local", "Admin12345!")
        repeat(3) { api.post("/api/v1/admin/categories", hidden("Pagină $it"), admin) }
        val first = api.get("/api/v1/admin/activity?action=createCategory&limit=2", admin)
        assertEquals(2, first["items"].size())
        val second = api.get("/api/v1/admin/activity?action=createCategory&limit=2&before=${first.text("nextCursor")}", admin)
        assertTrue(second["items"].none { e -> first["items"].any { it.path("id") == e.path("id") } })

        val old = UUID.randomUUID()
        jdbc.sql(
            """INSERT INTO activity_log (id, at, actor_id, actor_name, actor_roles, app, action, method, path, status)
               VALUES (:id, :at, :id, 'Old', '{WAITER}', 'WAITER', 'addTableRound', 'POST', '/x', 201)""",
        ).param("id", old).param("at", OffsetDateTime.now().minusDays(31)).update()
        activity.purge()
        assertNull(jdbc.sql("SELECT id FROM activity_log WHERE id = :id").param("id", old).query(UUID::class.java).optional().orElse(null))
        assertEquals(30, api.get("/api/v1/admin/settings", admin)["activityRetentionDays"].asInt())
    }
}
