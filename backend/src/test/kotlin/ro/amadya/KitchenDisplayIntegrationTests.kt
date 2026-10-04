package ro.amadya

import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Kitchen & bar display: per-station live feed of ticket changes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class)
@ActiveProfiles("test")
class KitchenDisplayIntegrationTests {

    @LocalServerPort
    private var port: Int = 0

    private val grill = "0190a000-0000-7000-8000-000000000102"
    private val bar = "0190a000-0000-7000-8000-000000000103"
    private val chickenPita = "0190a000-0000-7000-8000-000000000411"
    private val espresso = "0190a000-0000-7000-8000-000000000462"
    private val cheeseSauce = "0190a000-0000-7000-8000-000000000311"
    private val json = JsonMapper.builder().build()

    /** Collects `ticket` events from the SSE stream in the background. */
    private fun subscribe(token: String, station: String): MutableList<JsonNode> {
        val events = CopyOnWriteArrayList<JsonNode>()
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/kitchen/events?stationId=$station"))
            .header("Accept", "text/event-stream").header("Authorization", "Bearer $token").header("Accept-Language", "ro").GET().build()
        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept { response ->
            response.body().forEach { line -> if (line.startsWith("data:")) events.add(json.readTree(line.removePrefix("data:"))) }
        }
        Thread.sleep(500) // let the subscription register
        return events
    }

    @Test
    fun `display receives its station's tickets live, from pending to done`() {
        val api = ApiClient(port)
        val admin = api.login("admin@test.local", "Admin12345!")
        val email = "cook-${UUID.randomUUID()}@test.local"
        api.post("/api/v1/admin/users", mapOf("email" to email, "name" to "Cook", "password" to "Password123!", "roles" to listOf("KITCHEN", "CASHIER", "MANAGER")), admin)
        val cook = api.login(email, "Password123!")

        val stations = api.get("/api/v1/kitchen/stations", cook)
        assertEquals(200, stations.status)
        assertTrue(stations.body.any { it.path("code").asString() == "BAR" })

        val grillEvents = subscribe(cook, grill)
        val barEvents = subscribe(cook, bar)

        // One order with a grill dish and a bar drink -> one ticket per station.
        val order = api.post(
            "/api/v1/orders",
            mapOf("channel" to "COUNTER", "lines" to listOf(mapOf("productId" to chickenPita, "quantity" to 2, "modifierOptionIds" to listOf(cheeseSauce)), mapOf("productId" to espresso, "quantity" to 1))),
            cook,
        )
        val orderId = order.text("id")
        lateinit var ticketId: String
        await atMost Duration.ofSeconds(10) untilAsserted {
            val created = grillEvents.first { it.path("ticket").path("orderId").asString() == orderId }
            assertEquals("UPSERT", created.path("type").asString())
            assertEquals("QUEUED", created.path("ticket").path("status").asString())
            assertEquals("Lipie cu pui", created.path("ticket").path("lines")[0].path("productName").asString())
            assertEquals("Sos cheese", created.path("ticket").path("lines")[0].path("modifiers")[0].asString())
            ticketId = created.path("ticketId").asString()
            assertTrue(barEvents.any { it.path("ticket").path("lines")[0].path("productName").asString() == "Espresso" })
        }
        assertTrue(grillEvents.none { it.path("ticket").path("lines")[0].path("productName").asString() == "Espresso" }, "bar ticket must not reach the grill display")

        api.post("/api/v1/kitchen/tickets/$ticketId/start", token = cook)
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertTrue(grillEvents.any { it.path("ticketId").asString() == ticketId && it.path("ticket").path("status").asString() == "IN_PROGRESS" })
        }
        api.post("/api/v1/kitchen/tickets/$ticketId/ready", token = cook)
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertTrue(grillEvents.any { it.path("ticketId").asString() == ticketId && it.path("ticket").path("status").asString() == "READY" })
        }

        // A cancelled order disappears from the display.
        val other = api.post("/api/v1/orders", mapOf("channel" to "COUNTER", "lines" to listOf(mapOf("productId" to chickenPita, "quantity" to 1, "modifierOptionIds" to listOf(cheeseSauce)))), cook)
        lateinit var otherTicket: String
        await atMost Duration.ofSeconds(10) untilAsserted {
            otherTicket = grillEvents.first { it.path("ticket").path("orderId").asString() == other.text("id") }.path("ticketId").asString()
        }
        api.post("/api/v1/orders/${other.text("id")}/cancel", mapOf("reason" to "test"), cook)
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertTrue(grillEvents.any { it.path("ticketId").asString() == otherTicket && it.path("type").asString() == "REMOVED" })
        }

        // Takeaway tickets carry the customer's first name.
        val takeaway = api.post(
            "/api/v1/orders",
            mapOf("channel" to "TAKEAWAY", "customer" to mapOf("name" to "Ana Pop", "phone" to "+40722123456"), "lines" to listOf(mapOf("productId" to chickenPita, "quantity" to 1, "modifierOptionIds" to listOf(cheeseSauce)))),
            cook,
        )
        await atMost Duration.ofSeconds(10) untilAsserted {
            val t = grillEvents.first { it.path("ticket").path("orderId").asString() == takeaway.text("id") }
            assertEquals("Ana", t.path("ticket").path("customerName").asString())
        }
        assertEquals(403, api.get("/api/v1/kitchen/stations").status.let { if (it == 401) 403 else it })
    }
}
