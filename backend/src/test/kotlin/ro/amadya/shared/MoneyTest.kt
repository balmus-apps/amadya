package ro.amadya.shared

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class MoneyTest {

    @Test
    fun `vat is extracted from gross amounts`() {
        assertEquals(BigDecimal("2.48"), Money.vatOfGross(BigDecimal("25.00"), BigDecimal("11.00")))
        assertEquals(BigDecimal("2.78"), Money.vatOfGross(BigDecimal("16.00"), BigDecimal("21.00")))
        assertEquals(BigDecimal("0.00"), Money.vatOfGross(BigDecimal("10.00"), BigDecimal.ZERO))
    }

    @Test
    fun `amounts convert to minor units`() {
        assertEquals(4100L, Money.toMinorUnits(BigDecimal("41")))
        assertEquals(1999L, Money.toMinorUnits(BigDecimal("19.99")))
    }

    @Test
    fun `ids are time ordered version 7 uuids`() {
        val a = Ids.newId()
        Thread.sleep(2)
        val b = Ids.newId()
        assertEquals(7, a.version())
        assertEquals(2, a.variant())
        assert(a < b)
    }

    @Test
    fun `localized text falls back to romanian`() {
        val text = LocalizedText("Cartofi", null)
        assertEquals("Cartofi", text.resolve(java.util.Locale.ENGLISH))
        assertEquals("Fries", LocalizedText("Cartofi", "Fries").resolve(java.util.Locale.ENGLISH))
    }
}
