package ro.amadya

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import ro.amadya.procurement.internal.ExtractedInvoice
import ro.amadya.procurement.internal.Extraction
import ro.amadya.procurement.internal.InvoiceExtractor
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Photo → draft → reviewed save → stock items, secondary units and one draft NIR per warehouse, over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class, InvoiceScanIntegrationTests.FakeReader::class)
@ActiveProfiles("test")
class InvoiceScanIntegrationTests {

    /** Stands in for Claude: returns what it read from the Selgros delivery note photographed in the change proposal. */
    @TestConfiguration(proxyBeanMethods = false)
    class FakeReader {
        @Bean
        fun fakeInvoiceExtractor(): InvoiceExtractor = object : InvoiceExtractor {
            override fun extract(bytes: ByteArray, mediaType: String): Extraction {
                val raw = File("src/test/resources/invoices/selgros-aviz.json").readText()
                return Extraction(JsonMapper.builder().addModule(kotlinModule()).build().readValue(raw, ExtractedInvoice::class.java), raw)
            }
        }
    }

    @LocalServerPort
    private var port: Int = 0
    private lateinit var api: ApiClient
    private val http = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    private val ingredients = "0190a000-0000-7000-8000-000000001101"
    private val goods = "0190a000-0000-7000-8000-000000001102"
    private val garlicSauce = "0190a000-0000-7000-8000-000000001209" // stocked in ml
    private val kg = "0190a000-0000-7000-8000-000000001002"
    private val buc = "0190a000-0000-7000-8000-000000001007"

    /** Smallest bytes the media sniffer accepts as a JPEG; the fake reader ignores the content. */
    private val photo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10, 'J'.code.toByte(), 'F'.code.toByte())

    @BeforeEach
    fun setUp() {
        api = ApiClient(port)
    }

    private fun admin() = api.login("admin@test.local", "Admin12345!")

    private fun upload(path: String, bytes: ByteArray, filename: String, token: String): ApiClient.Response {
        val boundary = "----amadya${UUID.randomUUID()}"
        val body = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray() +
            bytes + "\r\n--$boundary--\r\n".toByteArray()
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Content-Type", "multipart/form-data; boundary=$boundary").header("Authorization", "Bearer $token")
            .header("Accept", "application/json, application/problem+json").header("Accept-Language", "en")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return ApiClient.Response(response.statusCode(), response.body().takeIf { it.isNotBlank() }?.let { json.readTree(it) } ?: json.createObjectNode())
    }

    @Suppress("UNCHECKED_CAST")
    private fun mutable(node: JsonNode): MutableMap<String, Any?> = json.convertValue(node, Map::class.java).toMutableMap() as MutableMap<String, Any?>

    @Test
    fun `scan a cash-and-carry delivery note, rename by rule, save with parities and one NIR per warehouse`() {
        val token = admin()
        assertEquals(true, api.get("/api/v1/admin/invoices/scan", token).body.path("enabled").asBoolean())

        // A rule the owner keeps: any garlic sauce becomes our "Sos de usturoi" stock item.
        val badRule = api.post("/api/v1/admin/product-name-rules", mapOf("matchType" to "REGEX", "pattern" to "([", "targetName" to "x"), token)
        assertEquals(422, badRule.status)
        val rule = api.post(
            "/api/v1/admin/product-name-rules",
            mapOf("matchType" to "CONTAINS", "pattern" to "SOS USTUROI|GARLIC SAUCE", "targetName" to "Sos de usturoi", "stockItemId" to garlicSauce),
            token,
        )
        assertEquals(201, rule.status, rule.body.toString())
        assertEquals("Sos de usturoi", api.post("/api/v1/admin/product-name-rules/test", mapOf("text" to "Univer sos usturoi 430g"), token).text("name"))

        val scan = upload("/api/v1/admin/invoices/scan", photo, "aviz.jpg", token)
        assertEquals(200, scan.status, scan.body.toString())
        val draft = scan["invoice"]
        assertEquals(0, scan["warnings"].size(), "totals add up: ${scan["warnings"]}")
        assertEquals("137.17", scan["printed"].path("totalGross").asString())
        assertEquals("AVIZ", draft.path("docType").asString())
        assertEquals("2636528510371144", draft.path("number").asString())
        assertEquals("2026-10-12", draft.path("issueDate").asString())
        assertEquals("09:38", draft.path("issueTime").asString())
        assertEquals("105", draft.path("registerNo").asString())
        assertEquals("ANTOANELA MOCANU", draft.path("buyerPerson").asString())
        assertEquals("CASH", draft.path("paymentMethod").asString())
        assertEquals("137.17", draft.path("paidAmount").asString())
        assertEquals("11805367", draft.path("supplier").path("cui").asString(), "stored without the RO prefix, as e-Factura imports")
        assertTrue(draft.path("supplierId").isMissingNode || draft.path("supplierId").isNull, "Selgros is not a supplier yet")
        val lines = draft.path("lines")
        assertEquals(5, lines.size())
        assertEquals("1.146", lines[0].path("quantity").asString())
        assertEquals(kg, lines[0].path("unitId").asString())
        assertEquals("Pleskavica sarbeasca(s) cg.kg", lines[0].path("description").asString())
        assertEquals("PLESKAVICA SARBEASCA(S) CG.KG", lines[0].path("originalDescription").asString())
        assertEquals(ingredients, lines[0].path("warehouseId").asString())
        assertEquals("Sos de usturoi", lines[2].path("description").asString())
        assertEquals(garlicSauce, lines[2].path("stockItemId").asString())
        assertEquals(buc, lines[2].path("unitId").asString(), "BU is a piece")
        assertEquals(rule.text("id"), scan["lineInfo"][2].path("ruleId").asString())

        // The stored original is private.
        val fileKey = draft.path("fileKey").asString()
        assertTrue(fileKey.startsWith("private-"))
        assertEquals(400, api.get("/api/v1/files/$fileKey").status, "private keys are not even valid public file keys")

        // Review: create the missing products (primary unit = the printed one) and give the jar of sauce its parity.
        val body = mutable(draft)
        val reviewed = (body["lines"] as List<*>).map { mutable(json.valueToTree(it)) }
        reviewed[0]["newStockItem"] = mapOf("name" to "Pleskavica", "unitId" to kg)
        reviewed[1]["newStockItem"] = mapOf("name" to "Pleskavica", "unitId" to kg)
        reviewed[1]["description"] = "Pleskavica"
        reviewed[0]["description"] = "Pleskavica"
        reviewed[3]["newStockItem"] = mapOf("name" to "Ketchup dulce", "unitId" to buc)
        reviewed[4]["newStockItem"] = mapOf("name" to "Castraveți murați", "unitId" to buc)
        body["lines"] = reviewed
        // Without the parity, 1 BU of a sauce stocked in ml does not convert, and nothing is saved.
        val noParity = api.post("/api/v1/admin/invoices", body, token)
        assertEquals(422, noParity.status, noParity.body.toString())
        assertTrue(api.get("/api/v1/admin/stock-items?q=Pleskavica", token).body.isEmpty, "rolled back")
        reviewed[2]["newPackaging"] = mapOf("name" to "borcan 430", "qtyInBase" to "430")
        reviewed[3]["warehouseId"] = goods
        val saved = api.post("/api/v1/admin/invoices", body, token)
        assertEquals(201, saved.status, saved.body.toString())
        assertEquals("SCAN", saved.text("source"))
        assertEquals(true, saved["hasFile"].asBoolean())
        assertEquals("121.33", saved.text("totalNet"))
        assertEquals("15.84", saved.text("totalVat"))
        assertEquals("137.17", saved.text("totalGross"))
        assertEquals("09:38", saved.text("issueTime"))
        assertEquals("Selgros Cash & Carry S.R.L.", saved.text("supplierName"))
        val savedLines = saved["lines"]
        assertEquals(savedLines[0].path("stockItemId").asString(), savedLines[1].path("stockItemId").asString(), "one Pleskavica item")
        assertTrue(savedLines.toList().all { it.path("matched").asBoolean() })
        assertNotNull(savedLines[2].path("packagingId").asString().ifEmpty { null }, "the jar is a secondary unit of the sauce")

        // One draft NIR per warehouse, nothing posted.
        val nirIds = saved["nirIds"].toList().map { it.asString() }
        assertEquals(2, nirIds.size)
        val nirs = nirIds.map { api.get("/api/v1/admin/nirs/$it", token) }
        val byWarehouse = nirs.associateBy { it.text("warehouseId") }
        assertEquals(4, byWarehouse.getValue(ingredients)["lines"].size())
        assertEquals(1, byWarehouse.getValue(goods)["lines"].size())
        assertTrue(nirs.all { it.text("status") == "DRAFT" })

        // The new item's primary unit is kg (display), stored in g.
        val pleskavica = api.get("/api/v1/admin/stock-items/${savedLines[0].path("stockItemId").asString()}", token)
        assertEquals("g", pleskavica.text("baseUnitCode"))
        assertEquals(kg, pleskavica.text("displayUnitId"))
        assertEquals(1, api.get("/api/v1/admin/stock-items/$garlicSauce", token)["packagings"].toList().count { it.path("name").asString() == "borcan 430" })

        // The original is served to staff only.
        val file = http.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/admin/invoices/${saved.text("id")}/file")).header("Authorization", "Bearer $token").build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        assertEquals(200, file.statusCode())
        assertEquals("image/jpeg", file.headers().firstValue("Content-Type").orElse(""))
        assertEquals(401, http.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/admin/invoices/${saved.text("id")}/file")).build(), HttpResponse.BodyHandlers.discarding()).statusCode())

        // Scanning it again: the supplier is known now, the jar's parity is remembered, and the duplicate is flagged.
        val again = upload("/api/v1/admin/invoices/scan", photo, "aviz.jpg", token)
        assertEquals(saved.text("supplierId"), again["invoice"].path("supplierId").asString())
        assertEquals(savedLines[2].path("packagingId").asString(), again["invoice"].path("lines")[2].path("packagingId").asString())
        assertEquals(savedLines[0].path("stockItemId").asString(), again["invoice"].path("lines")[0].path("stockItemId").asString(), "remembered by code")
        assertTrue(again["warnings"].toList().any { it.path("code").asString() == "DUPLICATE" }, again["warnings"].toString())
        assertEquals(409, api.post("/api/v1/admin/invoices", body, token).status)
    }

    @Test
    fun `manual entry needs a warehouse per line when it creates NIRs, and a PDF is a valid upload`() {
        val token = admin()
        val line = mutableMapOf<String, Any?>("description" to "Lapte", "quantity" to "2", "unitPrice" to "6.5", "vatPercent" to "11", "unitCode" to "l", "newStockItem" to mapOf("name" to "Lapte", "unitId" to "0190a000-0000-7000-8000-000000001005"))
        val invoice = mutableMapOf<String, Any?>(
            "supplier" to mapOf("name" to "Lactate Fulga SRL"), "number" to "F-100", "issueDate" to "2026-10-13", "createNirs" to true, "lines" to listOf(line),
        )
        assertEquals(422, api.post("/api/v1/admin/invoices", invoice, token).status)
        line["warehouseId"] = ingredients
        val saved = api.post("/api/v1/admin/invoices", invoice, token)
        assertEquals(201, saved.status, saved.body.toString())
        assertEquals("MANUAL", saved.text("source"))
        assertEquals("13.00", saved.text("totalNet"))
        assertEquals("14.43", saved.text("totalGross"))
        assertEquals(1, saved["nirIds"].toList().size)
        assertEquals(false, saved["hasFile"].asBoolean())

        assertEquals(201, upload("/api/v1/admin/files", "%PDF-1.7\n%âãÏÓ\n".toByteArray(), "doc.pdf", token).status)
        assertEquals(422, upload("/api/v1/admin/files", "not a pdf".toByteArray(), "doc.pdf", token).status)
    }
}
