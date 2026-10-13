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
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Online payment off (the default): takeaway orders go straight to the kitchen and are paid when picked up. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class)
@ActiveProfiles("test")
class PayAtPickupIntegrationTests {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jdbc: JdbcClient

    private lateinit var api: ApiClient

    private val cocaCola = "0190a000-0000-7000-8000-000000000451" // 8.00, no station: ready at once
    private val tableT4 = "0190a000-0000-7000-8000-000000000724"

    @BeforeEach
    fun setUp() {
        api = ApiClient(port)
        jdbc.onlinePayments(false)
        jdbc.takeawayConfirmMinutes(0) // confirmation by the cashier is covered by TakeawayConfirmationIntegrationTests
    }

    private fun admin() = api.login("admin@test.local", "Admin12345!")

    private fun cashier(): String {
        val email = "cashier-${UUID.randomUUID()}@test.local"
        api.post("/api/v1/admin/users", mapOf("email" to email, "name" to "Casier", "password" to "Password123!", "roles" to listOf("CASHIER")), admin())
        return api.login(email, "Password123!")
    }

    private fun colas(quantity: Int) = mapOf(
        "channel" to "TAKEAWAY",
        "customer" to mapOf("name" to "Ana Pop", "phone" to "+40722123456"),
        "lines" to listOf(mapOf("productId" to cocaCola, "quantity" to quantity)),
    )

    /** A customer's takeaway order, already READY because drinks need no preparation. */
    private fun readyOrder(quantity: Int, staff: String): String {
        val created = api.post("/api/v1/orders", colas(quantity))
        assertEquals(201, created.status, created.body.toString())
        assertEquals("PLACED", created.text("status"))
        assertEquals("UNPAID", created.text("paymentStatus"))
        val id = created.text("id")
        await atMost Duration.ofSeconds(10) untilAsserted { assertEquals("READY", api.get("/api/v1/orders/$id", staff).text("status")) }
        return id
    }

    private fun payments(orderId: String) =
        jdbc.sql("SELECT method, provider, amount, tendered, status, recorded_by FROM payment WHERE order_id = ?::uuid").param(orderId).query().listOfRows()

    @Test
    fun `the switch decides between paying online and paying at pickup`() {
        assertEquals(false, api.get("/api/v1/settings/public")["features"].path("onlinePayments").asBoolean())
        assertEquals("PLACED", api.post("/api/v1/orders", colas(1)).text("status"))

        jdbc.onlinePayments(true)
        assertEquals("PENDING_PAYMENT", api.post("/api/v1/orders", colas(1)).text("status"))
    }

    @Test
    fun `cash at pickup with change`() {
        val staff = cashier()
        val orderId = readyOrder(5, staff) // 40.00

        val missing = api.post("/api/v1/orders/$orderId/complete", token = staff, lang = "en")
        assertEquals(422, missing.status)
        assertEquals("order.payment_required", missing.text("code"))
        val low = api.post("/api/v1/orders/$orderId/complete", mapOf("method" to "CASH", "tendered" to "30"), staff)
        assertEquals(422, low.status)
        assertEquals("order.tendered_too_low", low.text("code"))
        assertEquals(422, api.post("/api/v1/orders/$orderId/complete", mapOf("method" to "CARD_POS", "tendered" to "50"), staff).status)
        assertEquals("READY", api.get("/api/v1/orders/$orderId", staff).text("status"))
        assertEquals("UNPAID", api.get("/api/v1/orders/$orderId", staff).text("paymentStatus"))

        val done = api.post("/api/v1/orders/$orderId/complete", mapOf("method" to "CASH", "tendered" to "50"), staff)
        assertEquals(200, done.status, done.body.toString())
        assertEquals("COMPLETED", done.text("status"))
        assertEquals("PAID", done.text("paymentStatus"))

        await atMost Duration.ofSeconds(10) untilAsserted {
            val rows = payments(orderId)
            assertEquals(1, rows.size)
            assertEquals("CASH", rows[0]["method"])
            assertEquals("in_person", rows[0]["provider"])
            assertEquals("CAPTURED", rows[0]["status"])
            assertEquals(0, BigDecimal("40.00").compareTo(rows[0]["amount"] as BigDecimal))
            assertEquals(0, BigDecimal("50.00").compareTo(rows[0]["tendered"] as BigDecimal))
        }
    }

    @Test
    fun `card at pickup`() {
        val staff = cashier()
        val orderId = readyOrder(1, staff)

        val done = api.post("/api/v1/orders/$orderId/complete", mapOf("method" to "CARD_POS"), staff)
        assertEquals("PAID", done.text("paymentStatus"))
        await atMost Duration.ofSeconds(10) untilAsserted {
            val rows = payments(orderId)
            assertEquals(1, rows.size)
            assertEquals("CARD_POS", rows[0]["method"])
            assertNull(rows[0]["tendered"])
        }
    }

    @Test
    fun `table rounds complete without a payment`() {
        val waiter = api.login("ospatar@amadya.local", "Ospatar12345!")
        val session = api.post("/api/v1/tables/$tableT4/sessions", mapOf("guests" to 2), waiter)
        assertEquals(201, session.status, session.body.toString())
        val round = api.post("/api/v1/tables/sessions/${session.text("id")}/rounds", mapOf("lines" to listOf(mapOf("productId" to cocaCola, "quantity" to 1, "modifierOptionIds" to emptyList<String>()))), waiter)
        val orderId = round["rounds"][0].path("orderId").asString()
        await atMost Duration.ofSeconds(10) untilAsserted { assertEquals("READY", api.get("/api/v1/orders/$orderId", waiter).text("status")) }

        val done = api.post("/api/v1/orders/$orderId/complete", token = waiter)
        assertEquals("COMPLETED", done.text("status"), done.body.toString())
        assertEquals("UNPAID", done.text("paymentStatus")) // settled on the table bill
        assertEquals(0, payments(orderId).size)
    }
}
