package ro.amadya.procurement

import org.junit.jupiter.api.Test
import ro.amadya.procurement.internal.NameMatchType
import ro.amadya.procurement.internal.ProductNameRules
import ro.amadya.procurement.internal.ProductNameRules.Rule
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductNameRulesTest {

    private val milk = Rule(UUID.randomUUID(), NameMatchType.CONTAINS, "LAPTE|MILK", "Lapte", null)
    private val garlic = Rule(UUID.randomUUID(), NameMatchType.CONTAINS, "sos usturoi", "Sos usturoi", null)
    private val ketchup = Rule(UUID.randomUUID(), NameMatchType.STARTS_WITH, "TOMI KETCHUP", "Ketchup dulce", null)
    private val tomatoes = Rule(UUID.randomUUID(), NameMatchType.REGEX, "^DEF\\.?\\s*CASTR", "Castraveți murați", null)

    private fun name(text: String, vararg rules: Rule) = ProductNameRules.match(rules.toList(), text)

    @Test
    fun `different brands and spellings become the same product`() {
        assertEquals("Lapte", name("LAPTE FULGA 3.5%", milk).name)
        assertEquals("Lapte", name("Lapte Zuzu 3,7% grăsime", milk).name)
        assertEquals("Lapte", name("Milk ZUZU 3.7% fat", milk).name)
        assertEquals(milk.id, name("Milk ZUZU 3.7% fat", milk).ruleId)
    }

    @Test
    fun `words match whole words only, ignoring case and diacritics`() {
        assertEquals("Laptele de pasăre", name("LAPTELE DE PASĂRE", milk).name, "LAPTELE is not LAPTE")
        assertEquals("Sos usturoi", name("UNIVER SOS USTUROI 430G", garlic).name)
        assertEquals("Sos usturoi", name("Univer sos usturoi 430g", garlic).name)
    }

    @Test
    fun `starts with and regex`() {
        assertEquals("Ketchup dulce", name("TOMI KETCHUP DULCE 1KG", ketchup).name)
        assertNull(name("X TOMI KETCHUP", ketchup).ruleId, "starts with means at the start")
        assertEquals("Castraveți murați", name("DEF.CASTR.OT.6-9CM 670G", tomatoes).name)
    }

    @Test
    fun `priority order is respected, first match wins`() {
        val generic = Rule(UUID.randomUUID(), NameMatchType.CONTAINS, "SOS", "Sos", null)
        assertEquals("Sos", name("UNIVER SOS USTUROI 430G", generic, garlic).name)
        assertEquals("Sos usturoi", name("UNIVER SOS USTUROI 430G", garlic, generic).name)
    }

    @Test
    fun `no match keeps the printed text in sentence case`() {
        val result = name("PLESKAVICA SARBEASCA(S) CG.KG", milk, garlic)
        assertEquals("Pleskavica sarbeasca(s) cg.kg", result.name)
        assertNull(result.ruleId)
    }

    @Test
    fun `a runaway regex is stopped and does not match`() {
        val evil = Rule(UUID.randomUUID(), NameMatchType.REGEX, "^(a+)+$", "Evil", null)
        val started = System.nanoTime()
        assertNull(name("a".repeat(40) + "!", evil).ruleId)
        assert((System.nanoTime() - started) / 1_000_000 < 2_000) { "regex was not stopped" }
    }
}
