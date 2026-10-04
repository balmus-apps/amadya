package ro.amadya.inventory.internal

import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.contract.model.MeasureUnit
import ro.amadya.contract.model.MeasureUnitRequest
import ro.amadya.contract.model.Packaging
import ro.amadya.contract.model.StockItem
import ro.amadya.contract.model.StockItemRequest
import ro.amadya.contract.model.Warehouse
import ro.amadya.contract.model.WarehouseRequest
import ro.amadya.inventory.StockCatalog
import ro.amadya.inventory.StockItemInfo
import ro.amadya.inventory.UnitInfo
import ro.amadya.inventory.WarehouseInfo
import ro.amadya.shared.ConflictException
import ro.amadya.shared.LocalizedText
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.UnprocessableException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import ro.amadya.contract.model.Dimension as DimensionDto
import ro.amadya.contract.model.UnitStatus as UnitStatusDto
import ro.amadya.contract.model.WarehouseType as WarehouseTypeDto

/** Units, warehouses and stock items (master data) plus unit conversion. */
@Service
@Transactional(readOnly = true)
class StockCatalogService(
    private val units: UnitRepository,
    private val warehouses: WarehouseRepository,
    private val items: StockItemRepository,
    private val jdbc: JdbcClient,
) : StockCatalog {

    // ---------------------------------------------------------------- StockCatalog
    override fun item(id: UUID): StockItemInfo? = items.findByIdOrNull(id)?.toInfo()

    override fun warehouse(id: UUID): WarehouseInfo? =
        warehouses.findByIdOrNull(id)?.let { WarehouseInfo(it.id, it.code, LocalizedText.fromMap(it.name)!!, it.type.name) }

    override fun toBase(stockItemId: UUID, quantity: BigDecimal, unitId: UUID?, packagingId: UUID?): BigDecimal {
        val item = items.findByIdOrNull(stockItemId) ?: throw UnprocessableException("catalog.invalid_reference", "Stock item")
        val factor = factor(item, unitId, packagingId) ?: throw UnprocessableException("inventory.unit_not_convertible", unitCode(unitId, packagingId) ?: "?", item.sku)
        return quantity.multiply(factor).setScale(3, RoundingMode.HALF_UP)
    }

    override fun isConvertible(stockItemId: UUID, unitId: UUID?, packagingId: UUID?): Boolean =
        items.findByIdOrNull(stockItemId)?.let { factor(it, unitId, packagingId) } != null

    @Transactional
    override fun resolveUnit(code: String): UnitInfo {
        val trimmed = code.trim()
        val unit = units.findByCodeOrAlias(trimmed)
            ?: units.save(UnitEntity(code = trimmed, name = mutableMapOf("ro" to trimmed), dimension = null, factor = null, status = UnitStatus.UNMAPPED))
        return UnitInfo(unit.id, unit.code, unit.status == UnitStatus.ACTIVE)
    }

    override fun unitCode(unitId: UUID?, packagingId: UUID?): String? = when {
        packagingId != null -> jdbc.sql("SELECT name FROM stock_item_packaging WHERE id = :id").param("id", packagingId).query(String::class.java).optional().orElse(null)
        unitId != null -> units.findByIdOrNull(unitId)?.code
        else -> null
    }

    override fun findItemByName(name: String): StockItemInfo? = items.findByName(name.trim())?.toInfo()

    private fun factor(item: StockItemEntity, unitId: UUID?, packagingId: UUID?): BigDecimal? {
        if (packagingId != null) return item.packagings.firstOrNull { it.id == packagingId }?.qtyInBase
        if (unitId == null || unitId == item.baseUnitId) return BigDecimal.ONE
        val base = units.findByIdOrNull(item.baseUnitId) ?: return null
        val unit = units.findByIdOrNull(unitId) ?: return null
        return unit.factor?.takeIf { unit.status == UnitStatus.ACTIVE && unit.dimension == base.dimension }
    }

    // ---------------------------------------------------------------- units
    fun listUnits(): List<MeasureUnit> = units.findAllByOrderByDimensionAscFactorAsc().map { it.toDto() }

    @Transactional
    fun createUnit(req: MeasureUnitRequest): MeasureUnit {
        if (units.existsByCodeIgnoreCase(req.code)) throw ConflictException("inventory.unit_code_taken", req.code)
        val unit = UnitEntity(code = req.code.trim(), name = LocalizedText.of(req.name).toMap(), dimension = Dimension.valueOf(req.dimension.value), factor = BigDecimal(req.factor))
        unit.aliases = req.aliases.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        return units.save(unit).toDto()
    }

    /** Also used to map an UNMAPPED unit found on an invoice. Reference units keep their factor (1). */
    @Transactional
    fun updateUnit(id: UUID, req: MeasureUnitRequest): MeasureUnit {
        val unit = units.findByIdOrNull(id) ?: throw NotFoundException("Unit")
        if (!unit.code.equals(req.code, true) && units.existsByCodeIgnoreCase(req.code)) throw ConflictException("inventory.unit_code_taken", req.code)
        val dimension = Dimension.valueOf(req.dimension.value)
        if (unit.isReference && (dimension != unit.dimension || BigDecimal(req.factor).compareTo(BigDecimal.ONE) != 0)) {
            throw ConflictException("inventory.reference_unit_locked", unit.code)
        }
        unit.code = req.code.trim()
        unit.name = LocalizedText.of(req.name).toMap()
        unit.dimension = dimension
        unit.factor = BigDecimal(req.factor)
        unit.aliases = req.aliases.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        unit.status = UnitStatus.ACTIVE
        return unit.toDto()
    }

    // ---------------------------------------------------------------- warehouses
    fun listWarehouses(): List<Warehouse> = warehouses.findAllByOrderByCodeAsc().map { it.toDto() }

    @Transactional
    fun createWarehouse(req: WarehouseRequest): Warehouse {
        if (warehouses.existsByCodeIgnoreCase(req.code)) throw ConflictException("inventory.warehouse_code_taken", req.code)
        return warehouses.save(WarehouseEntity(req.code, LocalizedText.of(req.name).toMap(), WarehouseType.valueOf(req.type.value), req.active ?: true)).toDto()
    }

    @Transactional
    fun updateWarehouse(id: UUID, req: WarehouseRequest): Warehouse {
        val w = warehouses.findByIdOrNull(id) ?: throw NotFoundException("Warehouse")
        if (!w.code.equals(req.code, true) && warehouses.existsByCodeIgnoreCase(req.code)) throw ConflictException("inventory.warehouse_code_taken", req.code)
        w.code = req.code
        w.name = LocalizedText.of(req.name).toMap()
        w.type = WarehouseType.valueOf(req.type.value)
        w.active = req.active ?: w.active
        return w.toDto()
    }

    fun defaultWarehouseFor(item: StockItemEntity): UUID =
        item.defaultWarehouseId ?: warehouses.findFirstByTypeAndActiveTrueOrderByCodeAsc(item.type)?.id
            ?: throw UnprocessableException("inventory.no_warehouse", item.type.name)

    // ---------------------------------------------------------------- stock items
    fun listItems(type: WarehouseTypeDto?, q: String?): List<StockItem> {
        val onHand = quantitiesOnHand()
        return items.search(type?.value, q?.trim()?.ifBlank { null }).map { it.toDto(onHand[it.id] ?: BigDecimal.ZERO) }
    }

    fun getItem(id: UUID): StockItem = loadItem(id).let { it.toDto(quantitiesOnHand()[it.id] ?: BigDecimal.ZERO) }

    fun loadItem(id: UUID): StockItemEntity = items.findByIdOrNull(id) ?: throw NotFoundException("Stock item")

    @Transactional
    fun createItem(req: StockItemRequest): StockItem {
        if (items.existsBySkuIgnoreCase(req.sku)) throw ConflictException("inventory.sku_taken", req.sku)
        val base = referenceUnit(req.dimension)
        val item = StockItemEntity(req.sku.trim(), LocalizedText.of(req.name).toMap(), WarehouseType.valueOf(req.type.value), base.id)
        apply(item, req)
        return items.save(item).toDto(BigDecimal.ZERO)
    }

    @Transactional
    fun updateItem(id: UUID, req: StockItemRequest): StockItem {
        val item = loadItem(id)
        if (!item.sku.equals(req.sku, true) && items.existsBySkuIgnoreCase(req.sku)) throw ConflictException("inventory.sku_taken", req.sku)
        val base = referenceUnit(req.dimension)
        if (base.id != item.baseUnitId) {
            val used = jdbc.sql("SELECT count(*) FROM stock_movement WHERE stock_item_id = :i").param("i", id).query(Long::class.java).single()
            if (used > 0) throw ConflictException("inventory.dimension_locked", item.sku)
            item.baseUnitId = base.id
        }
        item.sku = req.sku.trim()
        item.name = LocalizedText.of(req.name).toMap()
        item.type = WarehouseType.valueOf(req.type.value)
        apply(item, req)
        return item.toDto(quantitiesOnHand()[item.id] ?: BigDecimal.ZERO)
    }

    private fun apply(item: StockItemEntity, req: StockItemRequest) {
        req.displayUnitId?.let { if (!isConvertible(item, it)) throw UnprocessableException("inventory.unit_not_convertible", it, req.sku) }
        item.displayUnitId = req.displayUnitId
        req.defaultWarehouseId?.let { warehouses.findByIdOrNull(it) ?: throw UnprocessableException("catalog.invalid_reference", "Warehouse") }
        item.defaultWarehouseId = req.defaultWarehouseId
        item.minStock = req.minStock?.let { BigDecimal(it) } ?: BigDecimal.ZERO
        item.active = req.active ?: true
        val existing = item.packagings.associateBy { it.id }
        val next = req.packagings.orEmpty().map { p ->
            val entity = p.id?.let { existing[it] } ?: PackagingEntity(name = p.name, qtyInBase = BigDecimal(p.qtyInBase))
            entity.name = p.name.trim()
            entity.qtyInBase = BigDecimal(p.qtyInBase)
            entity.barcode = p.barcode
            entity
        }
        item.packagings.retainAll(next.toSet())
        next.filter { it !in item.packagings }.forEach { item.packagings.add(it) }
    }

    private fun isConvertible(item: StockItemEntity, unitId: UUID): Boolean {
        val base = units.findByIdOrNull(item.baseUnitId)
        val unit = units.findByIdOrNull(unitId)
        return unit != null && unit.status == UnitStatus.ACTIVE && unit.dimension == base?.dimension
    }

    private fun referenceUnit(dimension: DimensionDto) =
        units.findByDimensionAndIsReferenceTrue(Dimension.valueOf(dimension.value)) ?: throw UnprocessableException("inventory.no_reference_unit", dimension.value)

    private fun quantitiesOnHand(): Map<UUID, BigDecimal> =
        jdbc.sql("SELECT stock_item_id, sum(quantity) q FROM stock_balance GROUP BY stock_item_id")
            .query { rs, _ -> rs.getObject("stock_item_id", UUID::class.java) to rs.getBigDecimal("q") }.list().toMap()

    fun unitCodeOf(unitId: UUID): String = units.findByIdOrNull(unitId)?.code ?: "?"

    // ---------------------------------------------------------------- mapping
    private fun StockItemEntity.toInfo() = StockItemInfo(id, sku, LocalizedText.fromMap(name)!!, unitCodeOf(baseUnitId), type.name, defaultWarehouseId)

    private fun UnitEntity.toDto() = MeasureUnit(
        id = id,
        code = code,
        name = LocalizedText.fromMap(name)!!.toDto(),
        factor = factor?.stripTrailingZeros()?.toPlainString() ?: "",
        isReference = isReference,
        aliases = aliases,
        status = UnitStatusDto.forValue(status.name),
        dimension = dimension?.let { DimensionDto.forValue(it.name) },
    )

    private fun WarehouseEntity.toDto() = Warehouse(id, code, LocalizedText.fromMap(name)!!.toDto(), WarehouseTypeDto.forValue(type.name), active)

    private fun StockItemEntity.toDto(onHand: BigDecimal) = StockItem(
        id = id,
        sku = sku,
        name = LocalizedText.fromMap(name)!!.toDto(),
        type = WarehouseTypeDto.forValue(type.name),
        baseUnitId = baseUnitId,
        baseUnitCode = unitCodeOf(baseUnitId),
        minStock = minStock.qty(),
        active = active,
        packagings = packagings.map { Packaging(it.name, it.qtyInBase.qty(), it.id, it.barcode) },
        quantityOnHand = onHand.qty(),
        displayUnitId = displayUnitId,
        defaultWarehouseId = defaultWarehouseId,
    )
}

/** Quantity as an API string with 3 decimals. */
internal fun BigDecimal.qty(): String = setScale(3, RoundingMode.HALF_UP).toPlainString()
