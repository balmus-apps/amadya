package ro.amadya

import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Black-box tests over HTTP against PostgreSQL (Testcontainers) with the dev demo menu (BurRegescu) loaded.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
class ApiIntegrationTests {

    @LocalServerPort
    private var port: Int = 0
    private lateinit var api: ApiClient

    private val chickenPita = "0190a000-0000-7000-8000-000000000411"
    private val cocaCola = "0190a000-0000-7000-8000-000000000451"
    private val cheeseSauce = "0190a000-0000-7000-8000-000000000311"
    private val bacon = "0190a000-0000-7000-8000-000000000321"
    private val grill = "0190a000-0000-7000-8000-000000000102"
    private val guest = mapOf("name" to "Ana Pop", "phone" to "+40722123456")

    @BeforeEach
    fun setUp() {
        api = ApiClient(port)
    }

    private fun admin() = api.login("admin@test.local", "Admin12345!")

    private fun staff(role: String): String {
        val email = "${role.lowercase()}-${UUID.randomUUID()}@test.local"
        val created = api.post("/api/v1/admin/users", mapOf("email" to email, "name" to role, "password" to "Password123!", "roles" to listOf(role)), admin())
        assertEquals(201, created.status, created.body.toString())
        return api.login(email, "Password123!")
    }

    private fun pitaOrder(vararg options: String) = mapOf(
        "channel" to "TAKEAWAY",
        "customer" to guest,
        "lines" to listOf(
            mapOf("productId" to chickenPita, "quantity" to 1, "modifierOptionIds" to options.toList()),
            mapOf("productId" to cocaCola, "quantity" to 2),
        ),
    )

    // ---------------------------------------------------------------- settings & menu
    @Test
    fun `public settings and localized menu`() {
        val settings = api.get("/api/v1/settings/public")
        assertEquals(200, settings.status)
        assertEquals("BurRegescu", settings.text("name"))
        assertEquals("#FFC400", settings["theme"].path("primary").asString())

        val menu = api.get("/api/v1/menu", lang = "en")
        val categories = menu["categories"]
        assertEquals("Burgers", categories[0].path("name").asString())
        val pita = categories.flatMap { it.path("products") }.first { it.path("id").asString() == chickenPita }
        assertEquals("Chicken pita", pita.path("name").asString())
        assertEquals("20.00", pita.path("price").path("amount").asString())
        assertEquals("Choose your sauce", pita.path("modifierGroups")[0].path("name").asString())

        assertEquals("More flavour!", menu["promotions"][0].path("title").asString())
        assertEquals("20 LEI", menu["promotions"][0].path("badge").asString())
        assertEquals(chickenPita, menu["promotions"][0].path("productId").asString())

        val ro = api.get("/api/v1/menu", lang = "ro")
        assertEquals("Burgeri", ro["categories"][0].path("name").asString())
    }

    // ---------------------------------------------------------------- identity
    @Test
    fun `login, refresh rotation and reuse detection`() {
        val bad = api.post("/api/v1/auth/login", mapOf("email" to "admin@test.local", "password" to "wrong"))
        assertEquals(401, bad.status)
        assertEquals("auth.invalid_credentials", bad.text("code"))

        val tokens = api.post("/api/v1/auth/login", mapOf("email" to "admin@test.local", "password" to "Admin12345!"))
        val me = api.get("/api/v1/me", tokens.text("accessToken"))
        assertEquals("ADMIN", me["roles"][0].asString())

        val rotated = api.post("/api/v1/auth/refresh", mapOf("refreshToken" to tokens.text("refreshToken")))
        assertEquals(200, rotated.status)
        // Reusing the old refresh token revokes the whole family, including the rotated one.
        assertEquals(401, api.post("/api/v1/auth/refresh", mapOf("refreshToken" to tokens.text("refreshToken"))).status)
        assertEquals(401, api.post("/api/v1/auth/refresh", mapOf("refreshToken" to rotated.text("refreshToken"))).status)
    }

    @Test
    fun `roles are enforced`() {
        val kitchen = staff("KITCHEN")
        assertEquals(403, api.get("/api/v1/admin/users", kitchen).status)
        assertEquals(401, api.get("/api/v1/admin/users").status)
        assertEquals(200, api.get("/api/v1/kitchen/tickets", kitchen).status)
        val counter = mapOf("channel" to "COUNTER", "lines" to listOf(mapOf("productId" to cocaCola, "quantity" to 1)))
        assertEquals(403, api.post("/api/v1/orders", counter).status)
    }

    // ---------------------------------------------------------------- catalog admin
    @Test
    fun `admin manages categories, modifier groups and products`() {
        val token = admin()
        val category = api.post("/api/v1/admin/categories", mapOf("name" to mapOf("ro" to "Deserturi", "en" to "Desserts"), "sortOrder" to 70), token)
        assertEquals(201, category.status)
        val group = api.post(
            "/api/v1/admin/modifier-groups",
            mapOf("name" to mapOf("ro" to "Topping"), "minSelect" to 0, "maxSelect" to 2, "options" to listOf(mapOf("name" to mapOf("ro" to "Ciocolată"), "priceDelta" to "3"))),
            token,
        )
        assertEquals(201, group.status)
        val product = api.post(
            "/api/v1/admin/products",
            mapOf(
                "categoryId" to category.text("id"),
                "name" to mapOf("ro" to "Papanași", "en" to "Papanasi"),
                "price" to "24.50",
                "vatRateId" to "0190a000-0000-7000-8000-000000000002",
                "stationId" to "0190a000-0000-7000-8000-000000000101",
                "modifierGroupIds" to listOf(group.text("id")),
            ),
            token,
        )
        assertEquals(201, product.status, product.body.toString())
        assertEquals("24.50", product["price"].path("amount").asString())

        assertEquals(409, api.delete("/api/v1/admin/modifier-groups/${group.text("id")}", token).status)
        assertEquals(409, api.delete("/api/v1/admin/categories/${category.text("id")}", token).status)

        val menu = api.get("/api/v1/menu", lang = "en")
        assertTrue(menu["categories"].any { it.path("name").asString() == "Desserts" })

        assertEquals(204, api.delete("/api/v1/admin/products/${product.text("id")}", token).status)
        val after = api.get("/api/v1/menu")
        assertTrue(after["categories"].none { c -> c.path("products").any { it.path("id").asString() == product.text("id") } })
    }

    // ---------------------------------------------------------------- ordering
    @Test
    fun `takeaway validation is localized`() {
        val noCustomer = api.post("/api/v1/orders", pitaOrder(cheeseSauce) - "customer", lang = "ro")
        assertEquals(422, noCustomer.status)
        assertEquals("order.customer_required", noCustomer.text("code"))
        assertTrue(noCustomer.text("detail").contains("obligatorii"))

        val noSauce = api.post("/api/v1/orders", pitaOrder(), lang = "en")
        assertEquals(422, noSauce.status)
        assertEquals("catalog.modifier_min", noSauce.text("code"))
        assertTrue(noSauce.text("detail").contains("Alege sosul"))

        val badPhone = api.post("/api/v1/orders", pitaOrder(cheeseSauce) + ("customer" to mapOf("name" to "Ana", "phone" to "0722")))
        assertEquals(400, badPhone.status)
    }

    @Test
    fun `takeaway order from checkout to pickup`() {
        val created = api.post("/api/v1/orders", pitaOrder(cheeseSauce, bacon), lang = "en")
        assertEquals(201, created.status, created.body.toString())
        assertEquals("PENDING_PAYMENT", created.text("status"))
        assertEquals("41.00", created["total"].path("amount").asString()) // (20 + 5 bacon) + 2 x 8
        assertEquals("5.26", created["vatTotal"].path("amount").asString()) // 2.48 at 11% + 2.78 at 21%
        assertTrue(created.text("number").startsWith("B-"))
        val orderId = created.text("id")
        val token = created.text("trackingToken")

        // Only the tracking token (or staff) can see the order.
        assertEquals(404, api.get("/api/v1/orders/$orderId").status)
        assertEquals(404, api.get("/api/v1/orders/$orderId?token=nope").status)
        assertEquals("Cheese sauce", api.get("/api/v1/orders/$orderId?token=$token", lang = "en")["lines"][0].path("modifiers")[0].asString())

        // Pay (fake provider) — same intent on retry.
        val intent = api.post("/api/v1/orders/$orderId/payment-intent?token=$token")
        assertEquals(200, intent.status, intent.body.toString())
        assertEquals("fake", intent.text("provider"))
        assertEquals(intent.text("paymentId"), api.post("/api/v1/orders/$orderId/payment-intent?token=$token").text("paymentId"))
        assertEquals(204, api.post("/api/v1/payments/${intent.text("paymentId")}/simulate-capture").status)

        await atMost Duration.ofSeconds(10) untilAsserted {
            val order = api.get("/api/v1/orders/$orderId?token=$token")
            assertEquals("PLACED", order.text("status"))
            assertEquals("PAID", order.text("paymentStatus"))
            assertNotNull(order["estimatedReadyAt"].asString(null))
        }

        // Kitchen: only the pita needs preparation (the drinks have no station).
        val kitchen = staff("KITCHEN")
        val ticket = api.get("/api/v1/kitchen/tickets?stationId=$grill", kitchen).body.first { it.path("orderId").asString() == orderId }
        assertEquals("Lipie cu pui", ticket.path("lines")[0].path("productName").asString())
        assertEquals(1, ticket.path("lines").size())
        val ticketId = ticket.path("id").asString()

        assertEquals("IN_PROGRESS", api.post("/api/v1/kitchen/tickets/$ticketId/start", token = kitchen).text("status"))
        assertEquals("PREPARING", api.get("/api/v1/orders/$orderId?token=$token").text("status"))
        assertEquals(409, api.post("/api/v1/kitchen/tickets/$ticketId/start", token = kitchen).status)
        assertEquals("READY", api.post("/api/v1/kitchen/tickets/$ticketId/ready", token = kitchen).text("status"))
        assertEquals("READY", api.get("/api/v1/orders/$orderId?token=$token").text("status"))

        val board = api.get("/api/v1/queue")
        assertTrue(board["ready"].any { it.asString() == created.text("number") })

        val cashier = staff("CASHIER")
        assertEquals("COMPLETED", api.post("/api/v1/orders/$orderId/complete", token = cashier).text("status"))
        assertTrue(api.get("/api/v1/queue")["ready"].none { it.asString() == created.text("number") })
    }

    @Test
    fun `counter order with only drinks is ready immediately`() {
        val cashier = staff("CASHIER")
        val order = api.post("/api/v1/orders", mapOf("channel" to "COUNTER", "lines" to listOf(mapOf("productId" to cocaCola, "quantity" to 1))), cashier)
        assertEquals("PLACED", order.text("status"))
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertEquals("READY", api.get("/api/v1/orders/${order.text("id")}", cashier).text("status"))
        }
    }

    @Test
    fun `cancelling a paid order refunds it and clears the kitchen`() {
        val created = api.post("/api/v1/orders", pitaOrder(cheeseSauce))
        val orderId = created.text("id")
        val token = created.text("trackingToken")
        val intent = api.post("/api/v1/orders/$orderId/payment-intent?token=$token")
        api.post("/api/v1/payments/${intent.text("paymentId")}/simulate-capture")
        val kitchen = staff("KITCHEN")
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertTrue(api.get("/api/v1/kitchen/tickets", kitchen).body.any { it.path("orderId").asString() == orderId })
        }

        val manager = staff("MANAGER")
        val cancelled = api.post("/api/v1/orders/$orderId/cancel", mapOf("reason" to "Customer called"), manager)
        assertEquals("CANCELLED", cancelled.text("status"))

        await atMost Duration.ofSeconds(10) untilAsserted {
            assertEquals("REFUNDED", api.get("/api/v1/orders/$orderId", manager).text("paymentStatus"))
            assertTrue(api.get("/api/v1/kitchen/tickets", kitchen).body.none { it.path("orderId").asString() == orderId })
        }
    }

    @Test
    fun `order status is streamed over SSE`() {
        val created = api.post("/api/v1/orders", pitaOrder(cheeseSauce))
        val lines = api.sseLines("/api/v1/orders/${created.text("id")}/events?token=${created.text("trackingToken")}", 2)
        assertEquals("event:order-status", lines[0].replace(" ", ""))
        assertTrue(lines[1].contains("PENDING_PAYMENT"))

        val queue = api.sseLines("/api/v1/queue/events", 2)
        assertEquals("event:queue", queue[0].replace(" ", ""))
    }
}
