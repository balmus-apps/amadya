package ro.amadya

import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import ro.amadya.notifications.internal.SseHub
import ro.amadya.ordering.internal.OrderService
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The cashier app: phone orders wait for confirmation, counter orders are paid when taken, staff follow a live stream. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class)
@ActiveProfiles("test")
class TakeawayConfirmationIntegrationTests {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var hub: SseHub

    private lateinit var api: ApiClient
    private val json = JsonMapper.builder().build()

    private val chickenPita = "0190a000-0000-7000-8000-000000000411" // 20.00, grill
    private val cheeseSauce = "0190a000-0000-7000-8000-000000000311"
    private val cocaCola = "0190a000-0000-7000-8000-000000000451" // 8.00, no station

    @BeforeEach
    fun setUp() {
        api = ApiClient(port)
        jdbc.onlinePayments(false)
        jdbc.takeawayConfirmMinutes(5)
    }

    private fun admin() = api.login("admin@test.local", "Admin12345!")

    private fun cashier(): String {
        val email = "cashier-${UUID.randomUUID()}@test.local"
        api.post("/api/v1/admin/users", mapOf("email" to email, "name" to "Casier", "password" to "Password123!", "roles" to listOf("CASHIER")), admin())
        return api.login(email, "Password123!")
    }

    private val pita = listOf(mapOf("productId" to chickenPita, "quantity" to 1, "modifierOptionIds" to listOf(cheeseSauce)))

    private fun phoneOrder() = api.post(
        "/api/v1/orders",
        mapOf("channel" to "TAKEAWAY", "customer" to mapOf("name" to "Ana Pop", "phone" to "+40722123456"), "lines" to pita),
    ).also { assertEquals(201, it.status, it.body.toString()) }

    private fun hasTicket(orderId: String, token: String) =
        api.get("/api/v1/kitchen/tickets", token).body.any { it.path("orderId").asString() == orderId }

    // ---------------------------------------------------------------- confirmation
    @Test
    fun `a phone order waits for the cashier, who confirms it`() {
        val staff = cashier()
        val admin = admin()
        val order = phoneOrder()
        val id = order.text("id")
        assertEquals("AWAITING_CONFIRMATION", order.text("status"))
        assertEquals("UNPAID", order.text("paymentStatus"))
        Thread.sleep(500) // the kitchen listens asynchronously: give it the chance to (wrongly) create a ticket
        assertTrue(!hasTicket(id, admin), "no kitchen ticket before confirmation")

        val confirmed = api.post("/api/v1/orders/$id/confirm", token = staff)
        assertEquals(200, confirmed.status, confirmed.body.toString())
        assertEquals("PLACED", confirmed.text("status"))
        await atMost Duration.ofSeconds(10) untilAsserted { assertTrue(hasTicket(id, admin)) }

        assertEquals(409, api.post("/api/v1/orders/$id/confirm", token = staff).status)
        assertEquals(409, api.post("/api/v1/orders/$id/refuse", mapOf("reason" to "Prea târziu"), staff).status)
    }

    @Test
    fun `a refused order is cancelled with the reason the customer sees`() {
        val order = phoneOrder()
        val refused = api.post("/api/v1/orders/${order.text("id")}/refuse", mapOf("reason" to "Nu mai avem pui"), cashier())
        assertEquals(200, refused.status, refused.body.toString())
        assertEquals("CANCELLED", refused.text("status"))

        val seen = api.get("/api/v1/orders/${order.text("id")}?token=${order.text("trackingToken")}")
        assertEquals("CANCELLED", seen.text("status"))
        assertEquals("Nu mai avem pui", seen.text("cancelReason"))
        assertTrue(!hasTicket(order.text("id"), admin()))
    }

    @Test
    fun `an order nobody answers is confirmed automatically`() {
        val id = phoneOrder().text("id")
        orderService.confirmWaitingOrders()
        assertEquals("AWAITING_CONFIRMATION", api.get("/api/v1/orders/$id", admin()).text("status"))

        jdbc.sql("UPDATE customer_order SET created_at = now() - interval '6 minutes' WHERE id = ?::uuid").param(id).update()
        orderService.confirmWaitingOrders()
        assertEquals("PLACED", api.get("/api/v1/orders/$id", admin()).text("status"))
    }

    @Test
    fun `confirmation can be turned off, and paid online orders never wait`() {
        jdbc.takeawayConfirmMinutes(0)
        assertEquals("PLACED", phoneOrder().text("status"))

        jdbc.takeawayConfirmMinutes(5)
        jdbc.onlinePayments(true)
        val order = phoneOrder()
        assertEquals("PENDING_PAYMENT", order.text("status"))
        val intent = api.post("/api/v1/orders/${order.text("id")}/payment-intent?token=${order.text("trackingToken")}")
        api.post("/api/v1/payments/${intent.text("paymentId")}/simulate-capture")
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertEquals("PLACED", api.get("/api/v1/orders/${order.text("id")}", admin()).text("status"))
        }
    }

    @Test
    fun `the confirmation time is a restaurant setting from 0 to 60`() {
        val admin = admin()
        val settings = api.get("/api/v1/admin/settings", admin)
        assertEquals(5, settings["takeawayConfirmMinutes"].asInt())
        assertEquals(5, api.get("/api/v1/settings/public")["takeawayConfirmMinutes"].asInt())

        val body = json.convertValue(settings.body, Map::class.java).toMutableMap()
        assertEquals(400, api.put("/api/v1/admin/settings", body + ("takeawayConfirmMinutes" to 90), admin).status)
        val saved = api.put("/api/v1/admin/settings", body + ("takeawayConfirmMinutes" to 0), admin)
        assertEquals(200, saved.status, saved.body.toString())
        assertEquals(0, saved["takeawayConfirmMinutes"].asInt())
        api.put("/api/v1/admin/settings", body, admin)
    }

    @Test
    fun `waiting orders are not sales`() {
        val admin = admin()
        val today = LocalDate.now(ZoneId.of("Europe/Bucharest"))
        val before = api.get("/api/v1/admin/reports/dashboard?from=$today&to=$today", admin)["orders"].asInt()
        phoneOrder()
        assertEquals(before, api.get("/api/v1/admin/reports/dashboard?from=$today&to=$today", admin)["orders"].asInt())
    }

    // ---------------------------------------------------------------- counter orders paid when taken
    private fun payments(orderId: String) =
        jdbc.sql("SELECT method, amount, tendered FROM payment WHERE order_id = ?::uuid").param(orderId).query().listOfRows()

    private fun counter(payment: Map<String, String>?, token: String?) = api.post(
        "/api/v1/orders",
        mapOf("channel" to "COUNTER", "lines" to listOf(mapOf("productId" to cocaCola, "quantity" to 5))) + (payment?.let { mapOf("payment" to it) } ?: emptyMap()),
        token,
    )

    @Test
    fun `the cashier takes the money when the order is taken`() {
        val staff = cashier()
        val cash = counter(mapOf("method" to "CASH", "tendered" to "50"), staff) // 40.00
        assertEquals(201, cash.status, cash.body.toString())
        assertEquals("PAID", cash.text("paymentStatus"))
        await atMost Duration.ofSeconds(10) untilAsserted {
            val rows = payments(cash.text("id"))
            assertEquals(1, rows.size)
            assertEquals("CASH", rows[0]["method"])
            assertEquals(0, BigDecimal("40.00").compareTo(rows[0]["amount"] as BigDecimal))
            assertEquals(0, BigDecimal("50.00").compareTo(rows[0]["tendered"] as BigDecimal))
        }
        // Drinks are ready at once; a paid order is handed over without a payment.
        await atMost Duration.ofSeconds(10) untilAsserted { assertEquals("READY", api.get("/api/v1/orders/${cash.text("id")}", staff).text("status")) }
        assertEquals("COMPLETED", api.post("/api/v1/orders/${cash.text("id")}/complete", token = staff).text("status"))
        assertEquals(1, payments(cash.text("id")).size)

        val card = counter(mapOf("method" to "CARD_POS"), staff)
        assertEquals("PAID", card.text("paymentStatus"))
        await atMost Duration.ofSeconds(10) untilAsserted { assertEquals("CARD_POS", payments(card.text("id")).single()["method"]) }

        val low = counter(mapOf("method" to "CASH", "tendered" to "10"), staff)
        assertEquals(422, low.status)
        assertEquals("order.tendered_too_low", low.text("code"))

        val customer = api.post(
            "/api/v1/orders",
            mapOf("channel" to "TAKEAWAY", "customer" to mapOf("name" to "Ana", "phone" to "+40722123456"), "lines" to pita, "payment" to mapOf("method" to "CASH")),
        )
        assertEquals(422, customer.status)
        assertEquals("order.payment_counter_only", customer.text("code"))
    }

    // ---------------------------------------------------------------- staff stream
    @Test
    fun `staff follow every order on a live stream`() {
        assertEquals(401, api.get("/api/v1/orders/events").status)

        val staff = cashier()
        val subscribers = hub.subscriberCount()
        val lines = CompletableFuture.supplyAsync { api.sseLines("/api/v1/orders/events", 4, staff) }
        await atMost Duration.ofSeconds(10) untilAsserted { assertTrue(hub.subscriberCount() > subscribers) }

        val id = phoneOrder().text("id")
        api.post("/api/v1/orders/$id/confirm", token = staff)

        val events = lines.get(10, TimeUnit.SECONDS).filter { it.startsWith("data:") }.map { json.readTree(it.removePrefix("data:")) }
        assertEquals(listOf("AWAITING_CONFIRMATION", "PLACED"), events.filter { it.path("orderId").asString() == id }.map { it.path("status").asString() })
        assertEquals("TAKEAWAY", events[0].path("channel").asString())
    }
}
