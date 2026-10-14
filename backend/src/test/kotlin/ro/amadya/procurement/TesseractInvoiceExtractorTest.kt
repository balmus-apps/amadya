package ro.amadya.procurement

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import ro.amadya.procurement.internal.InvoiceExtractor
import ro.amadya.procurement.internal.InvoiceScanProperties
import ro.amadya.procurement.internal.TesseractInvoiceExtractor
import ro.amadya.procurement.internal.num
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TesseractInvoiceExtractorTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(InvoiceScanProperties::class)
    class Props

    private val json = JsonMapper.builder().build()

    private val runner = ApplicationContextRunner()
        .withBean(JsonMapper::class.java, { json })
        .withUserConfiguration(Props::class.java, TesseractInvoiceExtractor::class.java)

    @Test
    fun `the reader exists only when the feature is turned on`() {
        runner.run { assertTrue(it.getBeansOfType(InvoiceExtractor::class.java).isEmpty()) }
        runner.withPropertyValues("amadya.invoice-scan.enabled=true").run { assertEquals(1, it.getBeansOfType(InvoiceExtractor::class.java).size) }
    }

    @Test
    fun `a missing OCR program makes the reader unavailable instead of failing`() {
        val reader = TesseractInvoiceExtractor(InvoiceScanProperties(enabled = true, tesseractPath = "/nonexistent/tesseract"), json)
        assertFalse(reader.available())
    }

    /** Runs only where tesseract is installed (the API image, or `brew install tesseract tesseract-lang`). */
    @Test
    fun `the Selgros photo is read on this machine`() {
        val reader = TesseractInvoiceExtractor(InvoiceScanProperties(enabled = true), json)
        assumeTrue(reader.available(), "tesseract is not installed here")
        val result = reader.extract(File("src/test/resources/invoices/selgros.jpg").readBytes(), "image/jpeg")
        val x = result.invoice
        assertEquals("2636528510371144", x.number)
        assertEquals(5, x.lines.size)
        assertEquals(num("137,17"), num(x.totalGross))
        assertTrue(json.readTree(result.raw).path("text").asString().contains("Total marf"))
    }
}
