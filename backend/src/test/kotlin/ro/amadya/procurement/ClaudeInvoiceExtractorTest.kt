package ro.amadya.procurement

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import ro.amadya.procurement.internal.ClaudeInvoiceExtractor
import ro.amadya.procurement.internal.InvoiceExtractor
import ro.amadya.procurement.internal.InvoiceScanProperties
import ro.amadya.procurement.internal.InvoiceScanService
import ro.amadya.procurement.internal.num
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.io.File
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeInvoiceExtractorTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(InvoiceScanProperties::class)
    class Props

    private val json = JsonMapper.builder().addModule(kotlinModule()).build()

    private val runner = ApplicationContextRunner()
        .withBean(JsonMapper::class.java, { json })
        .withUserConfiguration(Props::class.java, ClaudeInvoiceExtractor::class.java)

    @Test
    fun `the Claude reader exists only when the feature is turned on`() {
        runner.run { assertTrue(it.getBeansOfType(InvoiceExtractor::class.java).isEmpty()) }
        runner.withPropertyValues("amadya.invoice-scan.enabled=false").run { assertTrue(it.getBeansOfType(InvoiceExtractor::class.java).isEmpty()) }
        runner.withPropertyValues("amadya.invoice-scan.enabled=true").run {
            assertEquals(1, it.getBeansOfType(InvoiceExtractor::class.java).size)
            assertEquals("claude-sonnet-5-5", it.getBean(InvoiceScanProperties::class.java).model)
        }
    }

    @Test
    fun `the schema is strict - every object lists all its fields as required and allows nothing else`() {
        fun check(node: Map<*, *>, path: String) {
            if (node["type"] == "object") {
                val props = (node["properties"] as Map<*, *>)
                assertEquals(props.keys, (node["required"] as List<*>).toSet(), "required at $path")
                assertEquals(false, node["additionalProperties"], "additionalProperties at $path")
                props.forEach { (k, v) -> check(v as Map<*, *>, "$path.$k") }
            }
            (node["items"] as? Map<*, *>)?.let { check(it, "$path[]") }
        }
        check(ClaudeInvoiceExtractor.SCHEMA, "$")
    }

    @Test
    fun `an answer is parsed into the extraction model`() {
        val extractor = ClaudeInvoiceExtractor(InvoiceScanProperties(enabled = true), json)
        val invoice = extractor.parse(File("src/test/resources/invoices/selgros-aviz.json").readText())
        assertEquals(5, invoice.lines.size)
        assertEquals("BU", invoice.lines[2].unit)
        assertEquals("RO11805367", invoice.supplier?.cui)
        assertEquals(2, invoice.payments.size)
    }

    @Test
    fun `printed numbers in Romanian and English formats`() {
        assertEquals(BigDecimal("28.240"), num("28,240"))
        assertEquals(BigDecimal("1.146"), num("1.146"))
        assertEquals(BigDecimal("1234.56"), num("1.234,56"))
        assertEquals(BigDecimal("1234.56"), num("1,234.56"))
        assertEquals(BigDecimal("-12.83"), num("-12,83 RON"))
        assertEquals(BigDecimal("1234567"), num("1.234.567"))
        assertNull(num(" "))
        assertNull(num("-"))
    }

    @Test
    fun `Romanian fiscal code check digit`() {
        assertTrue(InvoiceScanService.validCui("RO11805367"))
        assertTrue(InvoiceScanService.validCui("52934920"))
        assertFalse(InvoiceScanService.validCui("RO11805368"))
        assertFalse(InvoiceScanService.validCui("ABC"))
    }
}

/** The real SDK call against a local stub of the Messages API: request shape in, parsed extraction out. */
class ClaudeInvoiceExtractorHttpTest {

    private val json = JsonMapper.builder().addModule(kotlinModule()).build()

    @Test
    fun `sends the photo with the schema and parses the answer`() {
        val answer = File("src/test/resources/invoices/selgros-aviz.json").readText()
        var request: tools.jackson.databind.JsonNode? = null
        var beta: String? = null
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/messages") { exchange ->
            request = json.readTree(exchange.requestBody.readAllBytes())
            beta = exchange.requestHeaders.getFirst("anthropic-beta")
            val response = json.writeValueAsBytes(
                mapOf(
                    "id" to "msg_1", "type" to "message", "role" to "assistant", "model" to "claude-sonnet-5-5",
                    "content" to listOf(mapOf("type" to "text", "text" to answer)),
                    "stop_reason" to "end_turn", "stop_sequence" to null,
                    "usage" to mapOf("input_tokens" to 1500, "output_tokens" to 900),
                ),
            )
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val props = InvoiceScanProperties(enabled = true, apiKey = "test-key", baseUrl = "http://127.0.0.1:${server.address.port}")
            val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(40, 60, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
            val result = ClaudeInvoiceExtractor(props, json).extract(png, "image/png")

            assertEquals(5, result.invoice.lines.size)
            assertEquals("137.17", result.invoice.totalGross)
            assertEquals(answer.trim(), result.raw)
            val sent = request!!
            assertEquals("claude-sonnet-5-5", sent.path("model").asString())
            assertEquals("json_schema", sent.path("output_config").path("format").path("type").asString())
            assertEquals(false, sent.path("output_config").path("format").path("schema").path("additionalProperties").asBoolean())
            assertEquals("default", sent.path("fallbacks").asString())
            assertTrue(beta!!.contains("server-side-fallback-2026-07-01"))
            val blocks = sent.path("messages")[0].path("content")
            assertEquals("image", blocks[0].path("type").asString())
            assertEquals("image/png", blocks[0].path("source").path("media_type").asString())
        } finally {
            server.stop(0)
        }
    }
}
