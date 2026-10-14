package ro.amadya.procurement

import org.junit.jupiter.api.Test
import ro.amadya.procurement.internal.InvoiceScanService
import ro.amadya.procurement.internal.InvoiceTextParser
import ro.amadya.procurement.internal.num
import java.io.File
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The text is real Tesseract output (ron+eng, --psm 6) of the Selgros photo from the change proposal. */
class InvoiceTextParserTest {

    private val selgros = InvoiceTextParser.parse(File("src/test/resources/invoices/selgros-ocr.txt").readText())

    @Test
    fun `the Selgros template reads the header`() {
        assertEquals("selgros", selgros.template)
        val x = selgros.invoice
        assertEquals("AVIZ", x.docType)
        assertEquals("2636528510371144", x.number)
        assertEquals("2026-10-12", x.issueDate)
        assertEquals("0408401926", x.clientNo)
        assertEquals("9000858122", x.cardNo)
        assertEquals("105", x.registerNo)
        assertEquals("15600597", x.cashierNo)
        assertEquals("NARCLOD SRL", x.buyer?.name)
        assertEquals("52934920", x.buyer?.cui)
        assertEquals("11805367", x.supplier?.cui)
        assertEquals("Selgros Galaţi", x.supplierBranch)
    }

    @Test
    fun `the Selgros template reads every product row with its numbers`() {
        val lines = selgros.invoice.lines
        assertEquals(5, lines.size)
        assertEquals(listOf("1,146", "1,152", "2", "2", "2"), lines.map { it.quantity })
        assertEquals(listOf("32,36", "32,53", "19,38", "24,96", "12,10"), lines.map { it.net })
        assertEquals(listOf("KG", "KG", "BU", "BU", "BU"), lines.map { it.unit })
        assertEquals("5941168005368", lines[2].code)
        assertTrue(lines[2].description!!.startsWith("UNIVER SOS USTURO"), lines[2].description)
        assertEquals("5660410002", lines[1].lot)
        assertEquals("21,0", lines[3].vatPercent)
    }

    @Test
    fun `the Selgros template reads totals, VAT table and payments`() {
        val x = selgros.invoice
        assertEquals("121,33", x.totalNet)
        assertEquals("15,84", x.totalVat)
        assertEquals("137,17", x.totalGross)
        assertEquals(listOf("11,0" to "10,60", "21,0" to "5,24"), x.vatBreakdown.map { it.percent to it.vat })
        assertEquals(BigDecimal("137.17"), x.payments.mapNotNull { num(it.amount) }.fold(BigDecimal.ZERO, BigDecimal::add))
    }

    @Test
    fun `a misread decimal separator in the price is repaired from the line value`() {
        assertEquals("9.690", InvoiceTextParser.repairPrice("2", "9690", "19,38"))
        assertEquals("9,690", InvoiceTextParser.repairPrice("2", "9,690", "19,38"))
    }

    @Test
    fun `the generic template reads an unknown layout`() {
        val text = """
            FACTURA FISCALA
            Seria LAF Nr. 004512        Data: 03.10.2026
            Furnizor: Lactate Fulga S.R.L.
            CUI: RO14399840  Reg. Com.: J13/1234/2010
            IBAN: RO12 RZBR 0000 0600 0240 6165
            Cumparator: NARCLOD SRL
            CUI: 52934920
            Nr  Denumire                 UM   Cant   Pret      Valoare   TVA
            1   Lapte Fulga 3.5% 1L      BUC  12     6,50      78,00     11%
            2   Smantana 20% 400g        BUC  6      8,20      49,20     11%
            Total de plata                                     141,19
        """.trimIndent()
        val parsed = InvoiceTextParser.parse(text)
        assertEquals("generic", parsed.template)
        val x = parsed.invoice
        assertEquals("FACTURA", x.docType)
        assertEquals("2026-10-03", x.issueDate)
        assertEquals("14399840", x.supplier?.cui)
        assertEquals("52934920", x.buyer?.cui)
        assertEquals("141,19", x.totalGross)
        assertEquals(2, x.lines.size)
        assertEquals("Lapte Fulga 3.5% 1L", x.lines[0].description)
        assertEquals("BUC", x.lines[0].unit)
        assertEquals("12", x.lines[0].quantity)
        assertEquals("6,50", x.lines[0].unitPrice)
        assertEquals("11", x.lines[0].vatPercent)
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
