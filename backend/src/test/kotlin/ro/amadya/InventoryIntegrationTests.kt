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
import java.io.File
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** NIR → FIFO stock → sales consumption by recipe → cancellation, transfers, counts and reversals, over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class)
@ActiveProfiles("test")
class InventoryIntegrationTests {

    @LocalServerPort
    private var port: Int = 0
    private lateinit var api: ApiClient

    private val ingredients = "0190a000-0000-7000-8000-000000001101"
    private val beef = "0190a000-0000-7000-8000-000000001202"
    private val cheddar = "0190a000-0000-7000-8000-000000001203"
    private val bacon = "0190a000-0000-7000-8000-000000001204"
    private val bun = "0190a000-0000-7000-8000-000000001201"
    private val coleslaw = "0190a000-0000-7000-8000-000000001216"
    private val cola = "0190a000-0000-7000-8000-000000001221"
    private val colaCase = "0190a000-0000-7000-8000-000000001251"
    private val kg = "0190a000-0000-7000-8000-000000001002"
    private val classicBurger = "0190a000-0000-7000-8000-000000000401"
    private val supplier = "0190a000-0000-7000-8000-000000001401"

    @BeforeEach
    fun setUp() {
        api = ApiClient(port)
    }

    private fun admin() = api.login("admin@test.local", "Admin12345!")

    private fun balance(token: String, item: String, warehouse: String = ingredients): BigDecimal =
        api.get("/api/v1/admin/stock/balances?warehouseId=$warehouse", token).body
            .firstOrNull { it.path("stockItemId").asString() == item }?.path("quantity")?.asString()?.let(::BigDecimal) ?: BigDecimal.ZERO

    private fun assertSame(expected: BigDecimal, actual: BigDecimal) = assertEquals(0, expected.compareTo(actual), "expected $expected but was $actual")

    private fun importXml(token: String, xml: String): ApiClient.Response {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/admin/invoices/import/efactura"))
            .header("Content-Type", "application/xml").header("Accept", "application/json, application/problem+json")
            .header("Authorization", "Bearer $token").POST(HttpRequest.BodyPublishers.ofString(xml)).build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        return ApiClient.Response(response.statusCode(), tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()))
    }

    @Test
    fun `e-Factura to NIR to FIFO stock, sale consumption and cancellation`() {
        val token = admin()
        val xml = File("../docs/samples/efactura-demo.xml").readText()

        val invoice = importXml(token, xml)
        assertEquals(201, invoice.status, invoice.body.toString())
        assertEquals(supplier, invoice.text("supplierId"), "supplier found by CUI (RO prefix ignored)")
        assertEquals("554.00", invoice.text("totalNet"))
        val lines = invoice["lines"]
        assertTrue(lines[0].path("matched").asBoolean(), "beef matched by name, KGM = kg")
        val colaLine = lines[3]
        assertFalse(colaLine.path("matched").asBoolean(), "XBX is an unknown unit and the name differs")
        assertEquals(409, importXml(token, xml).status, "same invoice twice")

        val invoiceId = invoice.text("id")
        assertEquals(422, api.post("/api/v1/admin/nirs", mapOf("warehouseId" to ingredients, "invoiceId" to invoiceId), token).status)

        // Teach the system: "bax" from this supplier is a case of 24 cans.
        val matched = api.put(
            "/api/v1/admin/invoices/$invoiceId/lines/${colaLine.path("id").asString()}/match",
            mapOf("stockItemId" to cola, "packagingId" to colaCase, "remember" to true), token,
        )
        assertTrue(matched["lines"][3].path("matched").asBoolean())

        val draft = api.post("/api/v1/admin/nirs", mapOf("warehouseId" to ingredients, "invoiceId" to invoiceId, "committee" to listOf("Ion Popescu")), token)
        assertEquals(201, draft.status, draft.body.toString())
        assertEquals("DRAFT", draft.text("status"))
        val nirId = draft.text("id")

        // 9.5 kg of beef arrived instead of 10: a reason is mandatory.
        val nirLines: List<MutableMap<String, Any?>> = draft["lines"].toList().map { it: tools.jackson.databind.JsonNode ->
            mutableMapOf<String, Any?>(
                "stockItemId" to it.path("stockItemId").asString(), "unitId" to it.path("unitId").asString(null), "packagingId" to it.path("packagingId").asString(null),
                "quantityDocument" to it.path("quantityDocument").asString(), "quantityReceived" to it.path("quantityReceived").asString(),
                "unitPrice" to it.path("unitPrice").asString(), "vatPercent" to it.path("vatPercent").asString(),
            )
        }
        nirLines[0]["quantityReceived"] = "9.5"
        // Cola goes to the finished goods warehouse in real life; here everything is received into one NIR warehouse.
        assertEquals(422, api.put("/api/v1/admin/nirs/$nirId", mapOf("warehouseId" to ingredients, "lines" to nirLines), token).status)
        nirLines[0]["discrepancyReason"] = "lipsă la livrare"
        val updated = api.put("/api/v1/admin/nirs/$nirId", mapOf("warehouseId" to ingredients, "lines" to nirLines), token)
        assertEquals(200, updated.status, updated.body.toString())
        assertEquals("-0.500", updated["lines"][0].path("difference").asString())

        val beefBefore = balance(token, beef)
        val posted = api.post("/api/v1/admin/nirs/$nirId/post", token = token)
        assertEquals("POSTED", posted.text("status"), posted.body.toString())
        assertTrue(posted.text("number").startsWith("NIR-"))
        assertSame(beefBefore + BigDecimal("9500"), balance(token, beef))
        val colaLot = api.get("/api/v1/admin/stock/lots?stockItemId=$cola", token).body.first()
        assertEquals(0, BigDecimal(colaLot.path("unitCost").asString()).compareTo(BigDecimal("2")), "96 RON / 48 cans")
        assertEquals(409, api.put("/api/v1/admin/nirs/$nirId", mapOf("warehouseId" to ingredients, "lines" to nirLines), token).status, "posted NIR is immutable")

        // Sale: 2 classic burgers consume 300 g beef, 2 buns, 40 g cheddar, 40 g bacon (not in stock -> deficit).
        val cashier = api.post("/api/v1/admin/users", mapOf("email" to "cash-${UUID.randomUUID()}@test.local", "name" to "C", "password" to "Password123!", "roles" to listOf("CASHIER", "MANAGER")), token)
            .let { api.login(it.text("email"), "Password123!") }
        val beefAfterNir = balance(token, beef)
        val bunAfterNir = balance(token, bun)
        val baconBefore = balance(token, bacon)
        val order = api.post("/api/v1/orders", mapOf("channel" to "COUNTER", "lines" to listOf(mapOf("productId" to classicBurger, "quantity" to 2))), cashier)
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertSame(beefAfterNir - BigDecimal("300"), balance(token, beef))
            assertSame(bunAfterNir - BigDecimal("2"), balance(token, bun))
        }
        val baconLow = api.get("/api/v1/admin/stock/balances?lowOnly=true", token).body.first { it.path("stockItemId").asString() == bacon }
        assertTrue(BigDecimal(baconLow.path("quantity").asString()).signum() < 0, "bacon is in deficit")

        // Reversal is refused once the received beef was used.
        assertEquals(409, api.post("/api/v1/admin/nirs/$nirId/reverse", token = token).status)

        // Cancelling the order returns the ingredients to their lots.
        api.post("/api/v1/orders/${order.text("id")}/cancel", mapOf("reason" to "test"), cashier)
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertSame(beefAfterNir, balance(token, beef))
            assertSame(baconBefore, balance(token, bacon))
        }

        // A second invoice from the same supplier recognises the cola line from the remembered supplier code.
        val second = importXml(token, xml.replace("ADS 1042", "ADS 1043"))
        assertTrue(second["lines"][3].path("matched").asBoolean())
        assertEquals(colaCase, second["lines"][3].path("packagingId").asString())
    }

    @Test
    fun `deficit is settled at the real cost when stock arrives`() {
        val token = admin()
        val warehouse = api.post("/api/v1/admin/warehouses", mapOf("code" to "W${UUID.randomUUID().toString().take(6).uppercase()}", "name" to mapOf("ro" to "Test"), "type" to "INGREDIENTS"), token).text("id")
        // Waste 200 g of coleslaw that is not in stock -> deficit at cost 0.
        val waste = api.post("/api/v1/admin/stock/documents", mapOf("type" to "WASTE", "warehouseId" to warehouse, "lines" to listOf(mapOf("stockItemId" to coleslaw, "quantity" to "200"))), token)
        assertEquals(201, waste.status, waste.body.toString())
        assertSame(BigDecimal("-200.000"), balance(token, coleslaw, warehouse))

        // 1 kg arrives at 5 RON/kg: 800 g remain, worth 4.00 RON, and the waste is re-valued to 1.00 RON.
        val nir = api.post(
            "/api/v1/admin/nirs",
            mapOf("warehouseId" to warehouse, "supplierId" to supplier, "lines" to listOf(mapOf("stockItemId" to coleslaw, "unitId" to kg, "quantityDocument" to "1", "quantityReceived" to "1", "unitPrice" to "5", "vatPercent" to "11"))),
            token,
        )
        api.post("/api/v1/admin/nirs/${nir.text("id")}/post", token = token)
        val row = api.get("/api/v1/admin/stock/balances?warehouseId=$warehouse", token).body.first { it.path("stockItemId").asString() == coleslaw }
        assertEquals("800.000", row.path("quantity").asString())
        assertEquals("4.00", row.path("value").path("amount").asString())
        val wasteMovement = api.get("/api/v1/admin/stock/movements?warehouseId=$warehouse&stockItemId=$coleslaw", token).body.first { it.path("type").asString() == "WASTE" }
        assertEquals("-1.00", wasteMovement.path("cost").path("amount").asString())
    }

    @Test
    fun `transfer keeps FIFO cost, count posts the difference, unused NIR can be reversed`() {
        val token = admin()
        val code = "W${UUID.randomUUID().toString().take(6).uppercase()}"
        val source = api.post("/api/v1/admin/warehouses", mapOf("code" to "${code}A", "name" to mapOf("ro" to "Sursa"), "type" to "INGREDIENTS"), token).text("id")
        val target = api.post("/api/v1/admin/warehouses", mapOf("code" to "${code}B", "name" to mapOf("ro" to "Bar"), "type" to "INGREDIENTS"), token).text("id")
        fun receive(qtyKg: String, price: String): String {
            val n = api.post(
                "/api/v1/admin/nirs",
                mapOf("warehouseId" to source, "supplierId" to supplier, "lines" to listOf(mapOf("stockItemId" to cheddar, "unitId" to kg, "quantityDocument" to qtyKg, "quantityReceived" to qtyKg, "unitPrice" to price))),
                token,
            )
            return api.post("/api/v1/admin/nirs/${n.text("id")}/post", token = token).text("id")
        }
        receive("1", "40") // 1000 g at 0.04
        val second = receive("1", "50") // 1000 g at 0.05

        // Transfer 1.5 kg: 1000 g at 0.04 + 500 g at 0.05 = 65.00 RON, lots keep their cost.
        val transfer = api.post(
            "/api/v1/admin/stock/documents",
            mapOf("type" to "TRANSFER", "warehouseId" to source, "targetWarehouseId" to target, "lines" to listOf(mapOf("stockItemId" to cheddar, "quantity" to "1.5", "unitId" to kg))),
            token,
        )
        assertEquals(201, transfer.status, transfer.body.toString())
        assertEquals("65.00", transfer["totalCost"].path("amount").asString())
        assertTrue(transfer.text("number").startsWith("TR-"))
        val lots = api.get("/api/v1/admin/stock/lots?warehouseId=$target&stockItemId=$cheddar", token).body.toList().map { it: tools.jackson.databind.JsonNode -> BigDecimal(it.path("unitCost").asString()).stripTrailingZeros().toPlainString() }
        assertEquals(listOf("0.04", "0.05"), lots)

        // The second NIR's lot was partly transferred -> cannot be reversed.
        assertEquals(409, api.post("/api/v1/admin/nirs/$second/reverse", token = token).status)

        // Count: 450 g found instead of 500 g -> 50 g loss at 0.05.
        val count = api.post("/api/v1/admin/stock/documents", mapOf("type" to "COUNT", "warehouseId" to source, "lines" to listOf(mapOf("stockItemId" to cheddar, "quantity" to "450"))), token)
        assertEquals("-50.000", count["lines"][0].path("difference").asString())
        assertEquals("-2.50", count["totalCost"].path("amount").asString())

        // A fresh, untouched NIR can be reversed.
        val third = receive("2", "30")
        val reversal = api.post("/api/v1/admin/nirs/$third/reverse", token = token)
        assertEquals(200, reversal.status, reversal.body.toString())
        assertEquals("-2.000", reversal["lines"][0].path("quantityReceived").asString())
        assertEquals("REVERSED", api.get("/api/v1/admin/nirs/$third", token).text("status"))
        assertSame(BigDecimal("450.000"), balance(token, cheddar, source))
    }
}
