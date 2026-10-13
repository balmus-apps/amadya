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
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR 0010: guests scan the table's QR, order (confirmed by the waiter or automatically) and pay from the phone. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class)
@ActiveProfiles("test")
class QrTableOrderingIntegrationTests {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jdbc: JdbcClient

    private val espresso = "0190a000-0000-7000-8000-000000000462" // 9.00, bar
    private val lemonade = "0190a000-0000-7000-8000-000000000461" // 14.00, bar
    private val bar = "0190a000-0000-7000-8000-000000000103"

    @BeforeEach
    fun onlinePaymentsOn() = jdbc.onlinePayments(true)

    private fun line(product: String, quantity: Int) = mapOf("productId" to product, "quantity" to quantity, "modifierOptionIds" to emptyList<String>())
    private fun guest(token: String) = mapOf("X-Table-Guest" to token)
    private fun amount(node: JsonNode, field: String) = BigDecimal(node.path(field).path("amount").asString())
    private fun kinds(view: JsonNode) = view.path("activity").toList().map { it.path("kind").asString() }

    @Test
    fun `guests start a table, join, order and pay from their phones`() {
        val api = ApiClient(port)
        val waiter = api.login("ospatar@amadya.local", "Ospatar12345!")
        val admin = api.login("admin@test.local", "Admin12345!")
        val code = "demo-5"

        // What the QR shows before joining.
        val info = api.get("/api/v1/t/$code", lang = "ro")
        assertEquals(200, info.status, info.body.toString())
        assertEquals("5", info.text("tableLabel"))
        assertTrue(info["enabled"].asBoolean())
        assertFalse(info["occupied"].asBoolean())
        assertEquals(404, api.get("/api/v1/t/not-a-table").status)

        // Andrei starts the free table and owns it; nothing reaches the kitchen until a waiter confirms.
        val andrei = api.post("/api/v1/t/$code/join", mapOf("name" to "Andrei"))
        assertEquals(201, andrei.status, andrei.body.toString())
        val andreiToken = andrei.text("token")
        assertEquals("OWNER", andrei["view"].path("me").path("role").asString())
        assertEquals("AWAITING_CONFIRMATION", andrei["view"].path("status").asString())

        val sessionId = api.get("/api/v1/tables/floor", waiter).body[0].path("tables").first { it.path("label").asString() == "5" }.path("session").let {
            assertTrue(it.path("awaitingConfirmation").asBoolean())
            it.path("id").asString()
        }

        val ordered = api.post("/api/v1/t/$code/rounds", mapOf("lines" to listOf(line(espresso, 2))), headers = guest(andreiToken), lang = "ro")
        assertEquals(201, ordered.status, ordered.body.toString())
        assertEquals(1, ordered["pendingRounds"].size())
        val staff = api.get("/api/v1/tables/sessions/$sessionId", waiter, "ro").body
        val pendingRound = staff.path("pendingRounds")[0]
        assertEquals("Andrei", pendingRound.path("guestName").asString())
        assertEquals(0, staff.path("rounds").size(), "nothing in the kitchen yet")

        // Mircea asks to join; the owner lets him in. Someone else asks and the waiter refuses.
        val mircea = api.post("/api/v1/t/$code/join", mapOf("name" to "Mircea"))
        val mirceaToken = mircea.text("token")
        assertEquals("PENDING", mircea["view"].path("me").path("status").asString())
        assertEquals(0, mircea["view"].path("rounds").size(), "nothing of the table before being let in")
        assertEquals(403, api.post("/api/v1/t/$code/rounds", mapOf("lines" to listOf(line(espresso, 1))), headers = guest(mirceaToken)).status)
        val requests = api.get("/api/v1/t/$code/me", headers = guest(andreiToken))["joinRequests"]
        assertEquals("Mircea", requests[0].path("name").asString())
        assertEquals(403, api.post("/api/v1/t/$code/guests/${requests[0].path("id").asString()}/approve", headers = guest(mirceaToken)).status)
        assertEquals(200, api.post("/api/v1/t/$code/guests/${requests[0].path("id").asString()}/approve", headers = guest(andreiToken)).status)

        val stranger = api.post("/api/v1/t/$code/join", mapOf("name" to "Necunoscut"))
        val strangerId = stranger["view"].path("me").path("id").asString()
        assertEquals(200, api.post("/api/v1/tables/sessions/$sessionId/guests/$strangerId/reject", token = waiter).status)
        assertEquals(403, api.get("/api/v1/t/$code/me", headers = guest(stranger.text("token"))).status)

        // The waiter confirms Andrei's round: the table is confirmed with it, and the bar gets the ticket.
        val confirmed = api.post("/api/v1/tables/sessions/$sessionId/pending-rounds/${pendingRound.path("id").asString()}/confirm", token = waiter, lang = "ro")
        assertEquals(200, confirmed.status, confirmed.body.toString())
        assertFalse(confirmed["awaitingConfirmation"].asBoolean())
        assertEquals("Andrei", confirmed["rounds"][0].path("placedBy").asString())
        val espressoOrder = confirmed["rounds"][0].path("orderId").asString()
        await atMost Duration.ofSeconds(10) untilAsserted {
            val ticket = api.get("/api/v1/kitchen/tickets?stationId=$bar", admin).body.first { it.path("orderId").asString() == espressoOrder }
            assertEquals("5", ticket.path("tableLabel").asString())
        }

        // Mircea's round is not answered in time: it is confirmed automatically.
        api.post("/api/v1/t/$code/rounds", mapOf("lines" to listOf(line(lemonade, 1))), headers = guest(mirceaToken))
        jdbc.sql("UPDATE pending_round SET deadline = now() - interval '1 second' WHERE session_id = ?::uuid AND status = 'PENDING'").param(sessionId).update()
        await atMost Duration.ofSeconds(30) untilAsserted {
            assertEquals(2, api.get("/api/v1/tables/sessions/$sessionId", waiter).body.path("rounds").size())
        }
        val afterAuto = api.get("/api/v1/t/$code/me", headers = guest(mirceaToken), lang = "ro").body
        assertTrue(afterAuto.path("activity").any { it.path("kind").asString() == "ROUND_CONFIRMED" && it.path("automatic").asBoolean() })
        assertEquals(BigDecimal("32.00"), amount(afterAuto, "total"))
        assertTrue(afterAuto.path("rounds").none { it.has("placedBy") && !it.path("placedBy").isNull }, "the guests' bill has no names")

        // Mircea pays for his lemonade from the phone; while it is pending, nobody can pay for it again.
        val lemonadeLine = afterAuto.path("rounds").toList().flatMap { it.path("lines").toList() }.first { it.path("productName").asString() == "Limonadă cu mentă" }.path("lineId").asString()
        val intent = api.post("/api/v1/t/$code/payments", mapOf("items" to listOf(mapOf("lineId" to lemonadeLine, "quantity" to 1))), headers = guest(mirceaToken))
        assertEquals(201, intent.status, intent.body.toString())
        assertEquals("fake", intent.text("provider"))
        assertEquals(BigDecimal("14.00"), BigDecimal(intent["amount"].path("amount").asString()))
        assertEquals(422, api.post("/api/v1/t/$code/payments", mapOf("items" to listOf(mapOf("lineId" to lemonadeLine, "quantity" to 1))), headers = guest(andreiToken)).status)
        assertEquals(422, api.post("/api/v1/tables/sessions/$sessionId/payments", mapOf("method" to "CASH", "amount" to "32.00"), waiter).status, "the reserved 14.00 cannot be paid twice")
        val reserved = api.get("/api/v1/t/$code/me", headers = guest(andreiToken)).body
        assertEquals(BigDecimal("14.00"), amount(reserved, "pending"))
        assertEquals(BigDecimal("18.00"), amount(reserved, "payable"))

        api.post("/api/v1/payments/${intent.text("paymentId")}/simulate-capture")
        await atMost Duration.ofSeconds(10) untilAsserted {
            val view = api.get("/api/v1/t/$code/me", headers = guest(andreiToken), lang = "ro").body
            assertEquals(BigDecimal("14.00"), amount(view, "paid"))
            val paid = view.path("activity").first { it.path("kind").asString() == "PAID" }
            assertEquals("Mircea", paid.path("actor").asString())
            assertEquals("Limonadă cu mentă", paid.path("items")[0].path("name").asString())
        }

        // Andrei pays the rest: the table closes and its rounds are paid.
        val rest = api.post("/api/v1/t/$code/payments", mapOf("all" to true), headers = guest(andreiToken))
        assertEquals(BigDecimal("18.00"), BigDecimal(rest["amount"].path("amount").asString()))
        api.post("/api/v1/payments/${rest.text("paymentId")}/simulate-capture")
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertEquals("CLOSED", api.get("/api/v1/t/$code/me", headers = guest(andreiToken)).text("status"))
        }
        assertEquals("PAID", api.get("/api/v1/orders/$espressoOrder", waiter).text("paymentStatus"))
        val log = kinds(api.get("/api/v1/tables/sessions/$sessionId", waiter).body)
        assertEquals("JOINED", log.first())
        assertEquals(
            listOf("JOINED", "JOINED", "ORDERED", "ORDERED", "PAID", "PAID", "ROUND_CONFIRMED", "ROUND_CONFIRMED", "TABLE_CONFIRMED"),
            log.sorted(),
        )
        assertEquals(409, api.post("/api/v1/t/$code/rounds", mapOf("lines" to listOf(line(espresso, 1))), headers = guest(andreiToken)).status)
    }

    @Test
    fun `the waiter refuses a table nobody sits at, and owns a seated table's QR flow`() {
        val api = ApiClient(port)
        val waiter = api.login("ospatar@amadya.local", "Ospatar12345!")

        // A prank from a photo of the QR: the waiter refuses it and the table is free again.
        val prank = api.post("/api/v1/t/demo-6/join", mapOf("name" to "Glumeț"))
        val prankSession = api.get("/api/v1/tables/floor", waiter).body[0].path("tables").first { it.path("label").asString() == "6" }.path("session").path("id").asString()
        assertEquals("CLOSED", api.post("/api/v1/tables/sessions/$prankSession/reject", mapOf("reason" to "masă goală"), waiter).text("status"))
        assertEquals(403, api.get("/api/v1/t/demo-6/me", headers = guest(prank.text("token"))).status)
        assertFalse(api.get("/api/v1/t/demo-6")["occupied"].asBoolean())

        // A table the waiter seated: the first guest to scan becomes its owner straight away.
        val seated = api.post("/api/v1/tables/0190a000-0000-7000-8000-000000000722/sessions", mapOf("guests" to 2), waiter)
        val ana = api.post("/api/v1/t/demo-t2/join", mapOf("name" to "Ana"))
        assertEquals("ACTIVE", ana["view"].path("me").path("status").asString())
        assertEquals("OWNER", ana["view"].path("me").path("role").asString())
        assertEquals("OPEN", ana["view"].path("status").asString())

        // The waiter removes her: her phone no longer has access.
        val anaId = ana["view"].path("me").path("id").asString()
        assertEquals(200, api.post("/api/v1/tables/sessions/${seated.text("id")}/guests/$anaId/remove", token = waiter).status)
        assertEquals(403, api.get("/api/v1/t/demo-t2/me", headers = guest(ana.text("token"))).status)

        // A new QR code makes the old one useless.
        val tables = api.get("/api/v1/admin/tables", api.login("admin@test.local", "Admin12345!")).body
        val t3 = tables.first { it.path("label").asString() == "T3" }
        val fresh = api.post("/api/v1/admin/tables/${t3.path("id").asString()}/qr", token = api.login("admin@test.local", "Admin12345!"))
        assertTrue(fresh.text("qrCode") != "demo-t3")
        assertEquals(404, api.get("/api/v1/t/demo-t3").status)
        assertEquals(200, api.get("/api/v1/t/${fresh.text("qrCode")}").status)
    }
}
