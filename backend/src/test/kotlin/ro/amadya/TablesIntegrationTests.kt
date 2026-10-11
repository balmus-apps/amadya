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
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Waiter app: tables, rounds, the bill split every way, transfer and merge. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class)
@ActiveProfiles("test")
class TablesIntegrationTests {

    @LocalServerPort
    private var port: Int = 0

    private val table1 = "0190a000-0000-7000-8000-000000000711"
    private val table2 = "0190a000-0000-7000-8000-000000000712"
    private val table3 = "0190a000-0000-7000-8000-000000000713"
    private val table4 = "0190a000-0000-7000-8000-000000000714"
    private val terrace1 = "0190a000-0000-7000-8000-000000000721"
    private val salon = "0190a000-0000-7000-8000-000000000701"
    private val grill = "0190a000-0000-7000-8000-000000000102"
    private val bar = "0190a000-0000-7000-8000-000000000103"
    private val espresso = "0190a000-0000-7000-8000-000000000462" // 9.00, bar
    private val lemonade = "0190a000-0000-7000-8000-000000000461" // 14.00, bar
    private val chickenPita = "0190a000-0000-7000-8000-000000000411"
    private val cheeseSauce = "0190a000-0000-7000-8000-000000000311"
    private val json = JsonMapper.builder().build()

    private fun line(product: String, quantity: Int, vararg options: String) =
        mapOf("productId" to product, "quantity" to quantity, "modifierOptionIds" to options.toList())

    private fun free(table: JsonNode) = table.path("session").let { it.isMissingNode || it.isNull }

    private fun amount(node: JsonNode, field: String) = BigDecimal(node.path(field).path("amount").asString())

    private fun subscribe(token: String): MutableList<JsonNode> {
        val events = CopyOnWriteArrayList<JsonNode>()
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/tables/events"))
            .header("Accept", "text/event-stream").header("Authorization", "Bearer $token").GET().build()
        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept { response ->
            response.body().forEach { line -> if (line.startsWith("data:")) events.add(json.readTree(line.removePrefix("data:"))) }
        }
        Thread.sleep(500)
        return events
    }

    @Test
    fun `a table from seating to the paid bill`() {
        val api = ApiClient(port)
        val admin = api.login("admin@test.local", "Admin12345!")
        val waiter = api.login("ospatar@amadya.local", "Ospatar12345!") // seeded account

        val floor = api.get("/api/v1/tables/floor", waiter, "ro")
        assertEquals(200, floor.status)
        assertEquals(listOf("Salon", "Terasă"), floor.body.toList().map { it.path("name").asString() })
        assertTrue(floor.body[0].path("tables").any { it.path("label").asString() == "1" && free(it) })

        // Seat two guests; nobody else can take the table meanwhile.
        val opened = api.post("/api/v1/tables/$table1/sessions", mapOf("guests" to 2), waiter)
        assertEquals(201, opened.status, opened.body.toString())
        assertEquals("Andrei Ospătar", opened.text("waiterName"))
        val session = opened.text("id")
        assertEquals(409, api.post("/api/v1/tables/$table1/sessions", mapOf("guests" to 3), waiter).status)

        val events = subscribe(waiter)

        // Two rounds: drinks for the bar, then food for the grill.
        val drinks = api.post("/api/v1/tables/sessions/$session/rounds", mapOf("lines" to listOf(line(espresso, 3), line(lemonade, 1))), waiter, "ro")
        assertEquals(201, drinks.status, drinks.body.toString())
        val food = api.post("/api/v1/tables/sessions/$session/rounds", mapOf("lines" to listOf(line(chickenPita, 1, cheeseSauce)), "notes" to "fără ceapă"), waiter, "ro")
        val rounds = food["rounds"]
        assertEquals(2, rounds.size())
        val drinksOrder = rounds[0].path("orderId").asString()
        val foodOrder = rounds[1].path("orderId").asString()
        assertEquals(BigDecimal("41.00"), amount(rounds[0], "total"))
        val total = amount(food.body, "total")
        assertEquals(BigDecimal("41.00") + amount(rounds[1], "total"), total)

        // The grill ticket says which table it goes to.
        await atMost Duration.ofSeconds(10) untilAsserted {
            val ticket = api.get("/api/v1/kitchen/tickets?stationId=$grill", admin).body.first { it.path("orderId").asString() == foodOrder }
            assertEquals("1", ticket.path("tableLabel").asString())
            assertEquals("DINE_IN", ticket.path("channel").asString())
        }
        // Dine-in orders only come from a table.
        assertEquals(422, api.post("/api/v1/orders", mapOf("channel" to "DINE_IN", "lines" to listOf(line(espresso, 1))), admin).status)

        // The bar finishes the drinks: the waiter is told the round is ready.
        lateinit var barTicket: String
        await atMost Duration.ofSeconds(10) untilAsserted {
            barTicket = api.get("/api/v1/kitchen/tickets?stationId=$bar", admin).body.first { it.path("orderId").asString() == drinksOrder }.path("id").asString()
        }
        api.post("/api/v1/kitchen/tickets/$barTicket/ready", token = admin)
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertTrue(events.any { it.path("sessionId").asString() == session && it.path("kind").asString() == "ROUND_READY" && it.path("orderNumber").asString() == rounds[0].path("number").asString() })
        }
        val onFloor = api.get("/api/v1/tables/floor", waiter).body[0].path("tables").first { it.path("label").asString() == "1" }.path("session")
        assertEquals(1, onFloor.path("roundsReady").asInt())
        assertEquals(1, onFloor.path("roundsInKitchen").asInt())

        // Split by items: one guest pays one espresso by card, then the other two.
        val espressoLine = rounds[0].path("lines")[0].path("lineId").asString()
        val one = api.post("/api/v1/tables/sessions/$session/payments", mapOf("method" to "CARD_POS", "items" to listOf(mapOf("lineId" to espressoLine, "quantity" to 1))), waiter)
        assertEquals(201, one.status, one.body.toString())
        assertEquals(BigDecimal("9.00"), amount(one.body, "paid"))
        assertEquals(1, one["rounds"][0].path("lines")[0].path("paidQuantity").asInt())
        val two = api.post("/api/v1/tables/sessions/$session/payments", mapOf("method" to "CASH", "items" to listOf(mapOf("lineId" to espressoLine, "quantity" to 2))), waiter)
        assertEquals(BigDecimal("27.00"), amount(two.body, "paid"))
        assertEquals(422, api.post("/api/v1/tables/sessions/$session/payments", mapOf("method" to "CASH", "items" to listOf(mapOf("lineId" to espressoLine, "quantity" to 1))), waiter).status)
        assertEquals(422, api.post("/api/v1/tables/sessions/$session/payments", mapOf("method" to "CASH", "amount" to "9999.00"), waiter).status)

        // The card payment was a mistake: undo it, the espresso is unpaid again.
        val cardPayment = two["payments"].first { it.path("method").asString() == "CARD_POS" }.path("id").asString()
        val voided = api.post("/api/v1/tables/sessions/$session/payments/$cardPayment/void", token = waiter)
        assertEquals(200, voided.status, voided.body.toString())
        assertEquals(BigDecimal("18.00"), amount(voided.body, "paid"))
        assertEquals(2, voided["rounds"][0].path("lines")[0].path("paidQuantity").asInt())

        // Closing with money still owed is refused.
        assertEquals(409, api.post("/api/v1/tables/sessions/$session/close", token = waiter).status)

        // Equal split of the rest between two guests: the first pays cash with a 100 note and gets change.
        val remaining = amount(voided.body, "remaining")
        val half = remaining.divide(BigDecimal(2)).setScale(2, java.math.RoundingMode.DOWN)
        val first = api.post("/api/v1/tables/sessions/$session/payments", mapOf("method" to "CASH", "amount" to half.toPlainString(), "tendered" to "100.00"), waiter)
        assertEquals(201, first.status, first.body.toString())
        assertEquals(BigDecimal("100.00") - half, BigDecimal(first["payments"].last().path("change").path("amount").asString()))
        assertEquals("OPEN", first.text("status"))
        val last = api.post("/api/v1/tables/sessions/$session/payments", mapOf("method" to "CARD_POS", "amount" to (remaining - half).toPlainString()), waiter)
        assertEquals("CLOSED", last.text("status"), last.body.toString())
        assertEquals(BigDecimal("0.00"), amount(last.body, "remaining"))
        assertEquals(total, amount(last.body, "paid"))

        // The table is free again, and its rounds are paid and served.
        assertTrue(free(api.get("/api/v1/tables/floor", waiter).body[0].path("tables").first { it.path("label").asString() == "1" }))
        listOf(drinksOrder, foodOrder).forEach {
            val order = api.get("/api/v1/orders/$it", waiter)
            assertEquals("COMPLETED", order.text("status"))
            assertEquals("PAID", order.text("paymentStatus"))
        }
        assertEquals(409, api.post("/api/v1/tables/sessions/$session/rounds", mapOf("lines" to listOf(line(espresso, 1))), waiter).status)
    }

    @Test
    fun `guests move tables, tables merge, and an empty table is freed`() {
        val api = ApiClient(port)
        val waiter = api.login("ospatar@amadya.local", "Ospatar12345!")

        val a = api.post("/api/v1/tables/$table2/sessions", mapOf("guests" to 2), waiter).text("id")
        api.post("/api/v1/tables/sessions/$a/rounds", mapOf("lines" to listOf(line(lemonade, 2))), waiter)
        val cancelled = api.post("/api/v1/tables/sessions/$a/rounds", mapOf("lines" to listOf(line(espresso, 1))), waiter)["rounds"][1].path("orderId").asString()

        // A round sent by mistake is cancelled and leaves the bill (once the kitchen has queued it, as in real use:
        // cancelling while the kitchen is updating the same order is answered with a 409 to retry).
        val admin = api.login("admin@test.local", "Admin12345!")
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertTrue(api.get("/api/v1/kitchen/tickets?stationId=$bar", admin).body.any { it.path("orderId").asString() == cancelled })
        }
        assertEquals(200, api.post("/api/v1/orders/$cancelled/cancel", mapOf("reason" to "greșeală"), waiter).status)
        assertEquals(BigDecimal("28.00"), amount(api.get("/api/v1/tables/sessions/$a", waiter).body, "total"))

        // They move to table 3.
        val moved = api.post("/api/v1/tables/sessions/$a/transfer", mapOf("tableId" to table3), waiter)
        assertEquals(200, moved.status, moved.body.toString())
        assertEquals("3", moved.text("tableLabel"))
        val b = api.post("/api/v1/tables/$terrace1/sessions", mapOf("guests" to 3), waiter).text("id")
        assertEquals(409, api.post("/api/v1/tables/sessions/$b/transfer", mapOf("tableId" to table3), waiter).status)

        // Then join the terrace table: one bill, five guests, table 3 free.
        api.post("/api/v1/tables/sessions/$b/rounds", mapOf("lines" to listOf(line(espresso, 1))), waiter)
        val merged = api.post("/api/v1/tables/sessions/$a/merge", mapOf("targetSessionId" to b), waiter)
        assertEquals(200, merged.status, merged.body.toString())
        assertEquals(5, merged["guests"].asInt())
        assertEquals(BigDecimal("37.00"), amount(merged.body, "total"))
        assertEquals(3, merged["rounds"].size())
        assertEquals("MERGED", api.get("/api/v1/tables/sessions/$a", waiter).text("status"))
        assertEquals(201, api.post("/api/v1/tables/$table3/sessions", mapOf("guests" to 1), waiter).status)

        val paid = api.post("/api/v1/tables/sessions/$b/payments", mapOf("method" to "CASH", "amount" to "37.00"), waiter)
        assertEquals("CLOSED", paid.text("status"))

        // Guests who leave without ordering: the table is simply freed.
        val empty = api.post("/api/v1/tables/$table4/sessions", mapOf("guests" to 4), waiter).text("id")
        assertEquals("CLOSED", api.post("/api/v1/tables/sessions/$empty/close", token = waiter).text("status"))
    }

    @Test
    fun `managers set up the floor and the kitchen cannot take tables`() {
        val api = ApiClient(port)
        val admin = api.login("admin@test.local", "Admin12345!")
        val created = api.post("/api/v1/admin/tables", mapOf("areaId" to salon, "label" to "B${UUID.randomUUID().toString().take(4)}", "seats" to 2), admin)
        assertEquals(201, created.status, created.body.toString())
        assertEquals(409, api.post("/api/v1/admin/tables", mapOf("areaId" to salon, "label" to "t1", "seats" to 2), admin).status)

        val email = "cook-${UUID.randomUUID()}@test.local"
        api.post("/api/v1/admin/users", mapOf("email" to email, "name" to "Cook", "password" to "Password123!", "roles" to listOf("KITCHEN")), admin)
        val cook = api.login(email, "Password123!")
        assertEquals(403, api.get("/api/v1/tables/floor", cook).status)
        assertEquals(403, api.post("/api/v1/tables/${created.text("id")}/sessions", mapOf("guests" to 2), cook).status)
    }
}
