package ro.amadya.catalog

import ro.amadya.shared.LocalizedText
import java.math.BigDecimal
import java.util.UUID

/** Catalog operations used by other modules. */
interface ProductCatalog {
    /**
     * Validates the requested products and modifier choices and prices them from the current catalog.
     * Throws an UnprocessableException with a localized code when a product is unavailable or the choices break a group's rules.
     */
    fun priceLines(lines: List<LineSelection>): List<PricedLine>
}

data class LineSelection(val productId: UUID, val quantity: Int, val optionIds: List<UUID>, val notes: String?)

data class PricedLine(
    val productId: UUID,
    val name: LocalizedText,
    val stationId: UUID?,
    val prepTimeSec: Int,
    val vatPercent: BigDecimal,
    /** Gross unit price including modifiers. */
    val unitPrice: BigDecimal,
    val quantity: Int,
    val notes: String?,
    val modifiers: List<PricedModifier>,
)

data class PricedModifier(val optionId: UUID, val name: LocalizedText, val priceDelta: BigDecimal)
