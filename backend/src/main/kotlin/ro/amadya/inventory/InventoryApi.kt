package ro.amadya.inventory

import ro.amadya.shared.LocalizedText
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Stock operations for other modules (procurement posts NIRs through it). Quantities are in the item's base unit. */
interface StockLedger {
    fun receive(command: Receipt): UUID

    /** Undoes every receipt of a source document; fails with a conflict when any of its lots was already consumed. */
    fun reverseReceipts(sourceType: String, sourceId: UUID, reversalRef: String)
}

data class Receipt(
    val warehouseId: UUID,
    val stockItemId: UUID,
    val quantity: BigDecimal,
    val unitCost: BigDecimal,
    val receivedAt: Instant,
    val sourceType: String,
    val sourceId: UUID,
    val sourceRef: String,
)

/** Read access to stock master data and unit conversion. */
interface StockCatalog {
    fun item(id: UUID): StockItemInfo?
    fun warehouse(id: UUID): WarehouseInfo?

    /** Converts a quantity in the given unit or packaging to the item's base unit; throws when not convertible. */
    fun toBase(stockItemId: UUID, quantity: BigDecimal, unitId: UUID?, packagingId: UUID?): BigDecimal

    /** True when [toBase] would succeed (unit is mapped and has the item's dimension, or packaging belongs to the item). */
    fun isConvertible(stockItemId: UUID, unitId: UUID?, packagingId: UUID?): Boolean

    /** Finds a unit by code or alias (case-insensitive); unknown codes create an UNMAPPED unit for an admin to define. */
    fun resolveUnit(code: String): UnitInfo

    fun unitCode(unitId: UUID?, packagingId: UUID?): String?

    fun findItemByName(name: String): StockItemInfo?
}

data class StockItemInfo(val id: UUID, val sku: String, val name: LocalizedText, val baseUnitCode: String, val type: String, val defaultWarehouseId: UUID?)

data class WarehouseInfo(val id: UUID, val code: String, val name: LocalizedText, val type: String)

data class UnitInfo(val id: UUID, val code: String, val mapped: Boolean)
