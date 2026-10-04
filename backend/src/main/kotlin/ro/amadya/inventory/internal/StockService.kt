package ro.amadya.inventory.internal

import org.slf4j.LoggerFactory
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Limit
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.contract.model.Recipe
import ro.amadya.contract.model.RecipeLine
import ro.amadya.contract.model.RecipeRequest
import ro.amadya.contract.model.StockBalance
import ro.amadya.contract.model.StockDocument
import ro.amadya.contract.model.StockDocumentLine
import ro.amadya.contract.model.StockDocumentRequest
import ro.amadya.contract.model.StockLot
import ro.amadya.contract.model.StockMovement
import ro.amadya.inventory.Receipt
import ro.amadya.inventory.StockLedger
import ro.amadya.ordering.OrderCancelled
import ro.amadya.ordering.OrderPlaced
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.Actor
import ro.amadya.shared.AmadyaProperties
import ro.amadya.shared.DocumentNumbers
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.UnprocessableException
import ro.amadya.shared.localized
import ro.amadya.shared.toMoney
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import ro.amadya.contract.model.StockDocumentType as StockDocumentTypeDto
import ro.amadya.contract.model.StockMovementType as StockMovementTypeDto

@Service
@Transactional(readOnly = true)
class StockService(
    private val ledger: FifoLedger,
    private val catalog: StockCatalogService,
    private val documents: StockDocumentRepository,
    private val recipes: RecipeLineRepository,
    private val warehouses: WarehouseRepository,
    private val numbers: DocumentNumbers,
    private val settings: RestaurantSettingsApi,
    private val props: AmadyaProperties,
    private val jdbc: JdbcClient,
    private val json: tools.jackson.databind.json.JsonMapper,
    private val clock: Clock,
) : StockLedger {

    private val log = LoggerFactory.getLogger(javaClass)

    // ---------------------------------------------------------------- StockLedger (procurement)
    @Transactional
    override fun receive(command: Receipt): UUID {
        catalog.loadItem(command.stockItemId)
        warehouses.findByIdOrNull(command.warehouseId) ?: throw UnprocessableException("catalog.invalid_reference", "Warehouse")
        return ledger.receive(
            command.warehouseId, command.stockItemId, command.quantity, command.unitCost, command.receivedAt, "RECEIPT_NIR",
            Source(command.sourceType, command.sourceId, command.sourceRef, Actor.current()?.userId),
        )
    }

    @Transactional
    override fun reverseReceipts(sourceType: String, sourceId: UUID, reversalRef: String) = ledger.reverseReceipts(sourceType, sourceId, reversalRef)

    // ---------------------------------------------------------------- sales consumption
    /** Ingredients leave stock when the order reaches the kitchen (recipes of products and chosen options). */
    @ApplicationModuleListener
    fun on(event: OrderPlaced) {
        val already = jdbc.sql("SELECT count(*) FROM stock_movement WHERE source_type = 'ORDER' AND source_id = :o").param("o", event.orderId).query(Long::class.java).single()
        if (already > 0) return
        val byProduct = recipes.findAllByProductIdIn(event.lines.map { it.productId }.toSet()).groupBy { it.productId!! }
        val byOption = recipes.findAllByOptionIdIn(event.lines.flatMap { it.modifierOptionIds }.toSet()).groupBy { it.optionId!! }
        val needs = mutableMapOf<Pair<UUID, UUID>, BigDecimal>()
        for (line in event.lines) {
            val recipe = byProduct[line.productId].orEmpty() + line.modifierOptionIds.flatMap { byOption[it].orEmpty() }
            for (r in recipe) {
                val warehouse = r.warehouseId ?: catalog.defaultWarehouseFor(catalog.loadItem(r.stockItemId))
                needs.merge(warehouse to r.stockItemId, r.quantity.multiply(BigDecimal(line.quantity)), BigDecimal::add)
            }
        }
        val source = Source("ORDER", event.orderId, event.number)
        needs.forEach { (key, qty) -> ledger.issue(key.first, key.second, qty, "CONSUMPTION_SALE", source) }
        if (needs.isNotEmpty()) log.info("Order {}: {} stock line(s) consumed", event.number, needs.size)
    }

    /** A cancelled order gives its ingredients back to the same lots. */
    @ApplicationModuleListener
    fun on(event: OrderCancelled) {
        val returned = jdbc.sql("SELECT count(*) FROM stock_movement WHERE source_type = 'ORDER_RETURN' AND source_id = :o").param("o", event.orderId).query(Long::class.java).single()
        if (returned == 0L) ledger.returnIssues("ORDER", event.orderId, "RETURN_SALE", null)
    }

    // ---------------------------------------------------------------- queries
    fun balances(warehouseId: UUID?, lowOnly: Boolean): List<StockBalance> {
        val locale = LocaleContextHolder.getLocale()
        val currency = settings.currency()
        return jdbc.sql(
            """SELECT b.warehouse_id, b.stock_item_id, i.sku, i.name, b.quantity, b.value, i.min_stock, u.code
               FROM stock_balance b JOIN stock_item i ON i.id = b.stock_item_id JOIN unit_of_measure u ON u.id = i.base_unit_id
               WHERE (CAST(:w AS uuid) IS NULL OR b.warehouse_id = :w)
               ORDER BY lower(i.name->>'ro')""",
        ).param("w", warehouseId).query { rs, _ ->
            val qty = rs.getBigDecimal("quantity")
            val min = rs.getBigDecimal("min_stock")
            StockBalance(
                warehouseId = rs.getObject("warehouse_id", UUID::class.java),
                stockItemId = rs.getObject("stock_item_id", UUID::class.java),
                sku = rs.getString("sku"),
                itemName = jsonName(rs.getString("name")).localized(locale),
                quantity = qty.qty(),
                unitCode = rs.getString("code"),
                value = rs.getBigDecimal("value").toMoney(currency),
                minStock = min.qty(),
                low = qty < min || qty.signum() < 0,
            )
        }.list().filter { !lowOnly || it.low }
    }

    fun movements(warehouseId: UUID?, itemId: UUID?, limit: Int): List<StockMovement> {
        val locale = LocaleContextHolder.getLocale()
        val currency = settings.currency()
        return jdbc.sql(
            """SELECT m.*, i.name, u.code FROM stock_movement m JOIN stock_item i ON i.id = m.stock_item_id JOIN unit_of_measure u ON u.id = i.base_unit_id
               WHERE (CAST(:w AS uuid) IS NULL OR m.warehouse_id = :w) AND (CAST(:i AS uuid) IS NULL OR m.stock_item_id = :i)
               ORDER BY m.occurred_at DESC, m.created_at DESC LIMIT :l""",
        ).param("w", warehouseId).param("i", itemId).param("l", limit).query { rs, _ ->
            StockMovement(
                id = rs.getObject("id", UUID::class.java),
                warehouseId = rs.getObject("warehouse_id", UUID::class.java),
                stockItemId = rs.getObject("stock_item_id", UUID::class.java),
                itemName = jsonName(rs.getString("name")).localized(locale),
                type = StockMovementTypeDto.forValue(rs.getString("type")),
                quantity = rs.getBigDecimal("quantity").qty(),
                unitCode = rs.getString("code"),
                cost = rs.getBigDecimal("total_cost").toMoney(currency),
                occurredAt = rs.getTimestamp("occurred_at").toInstant().atOffset(ZoneOffset.UTC),
                sourceType = rs.getString("source_type"),
                sourceId = rs.getObject("source_id", UUID::class.java),
                sourceRef = rs.getString("source_ref"),
            )
        }.list()
    }

    fun lots(warehouseId: UUID?, itemId: UUID?): List<StockLot> =
        jdbc.sql(
            """SELECT l.*, u.code FROM stock_lot l JOIN stock_item i ON i.id = l.stock_item_id JOIN unit_of_measure u ON u.id = i.base_unit_id
               WHERE l.qty_remaining > 0 AND (CAST(:w AS uuid) IS NULL OR l.warehouse_id = :w) AND (CAST(:i AS uuid) IS NULL OR l.stock_item_id = :i)
               ORDER BY l.received_at, l.seq""",
        ).param("w", warehouseId).param("i", itemId).query { rs, _ ->
            StockLot(
                id = rs.getObject("id", UUID::class.java),
                warehouseId = rs.getObject("warehouse_id", UUID::class.java),
                stockItemId = rs.getObject("stock_item_id", UUID::class.java),
                receivedAt = rs.getTimestamp("received_at").toInstant().atOffset(ZoneOffset.UTC),
                quantityInitial = rs.getBigDecimal("qty_initial").qty(),
                quantityRemaining = rs.getBigDecimal("qty_remaining").qty(),
                unitCost = rs.getBigDecimal("unit_cost").toPlainString(),
                unitCode = rs.getString("code"),
            )
        }.list()

    // ---------------------------------------------------------------- stock documents
    fun listDocuments(type: StockDocumentTypeDto?, limit: Int): List<StockDocument> =
        documents.search(type?.let { DocumentType.valueOf(it.value) }, Limit.of(limit)).map { it.toDto() }

    fun getDocument(id: UUID): StockDocument = (documents.findByIdOrNull(id) ?: throw NotFoundException("Stock document")).toDto()

    @Transactional
    fun createDocument(req: StockDocumentRequest): StockDocument {
        val type = DocumentType.valueOf(req.type.value)
        warehouses.findByIdOrNull(req.warehouseId) ?: throw UnprocessableException("catalog.invalid_reference", "Warehouse")
        if (type == DocumentType.TRANSFER) {
            val target = req.targetWarehouseId ?: throw UnprocessableException("inventory.transfer_target_required")
            if (target == req.warehouseId) throw UnprocessableException("inventory.transfer_same_warehouse")
            warehouses.findByIdOrNull(target) ?: throw UnprocessableException("catalog.invalid_reference", "Warehouse")
        }
        val date = req.date ?: LocalDate.now(clock.withZone(props.timezone))
        val doc = StockDocumentEntity(
            number = numbers.next(type.prefix, date.year),
            type = type,
            date = date,
            warehouseId = req.warehouseId,
            targetWarehouseId = req.targetWarehouseId.takeIf { type == DocumentType.TRANSFER },
            note = req.note?.trim()?.ifBlank { null },
            userId = Actor.current()?.userId,
        )
        documents.save(doc)
        val source = Source("DOCUMENT", doc.id, doc.number, doc.userId)
        var total = BigDecimal.ZERO
        req.lines.forEachIndexed { index, l ->
            val qty = catalog.toBase(l.stockItemId, BigDecimal(l.quantity), l.unitId, l.packagingId)
            val line = StockDocumentLineEntity(position = index + 1, stockItemId = l.stockItemId, quantity = qty, reason = l.reason)
            when (type) {
                DocumentType.TRANSFER -> line.cost = positive(qty).let { ledger.transfer(doc.warehouseId, doc.targetWarehouseId!!, l.stockItemId, it, source) }
                DocumentType.CONSUMPTION -> line.cost = ledger.issue(doc.warehouseId, l.stockItemId, positive(qty), "CONSUMPTION_NOTE", source).cost
                DocumentType.WASTE -> line.cost = ledger.issue(doc.warehouseId, l.stockItemId, positive(qty), "WASTE", source).cost
                DocumentType.COUNT -> {
                    if (qty.signum() < 0) throw UnprocessableException("inventory.quantity_positive")
                    val system = ledger.quantityOnHand(doc.warehouseId, l.stockItemId)
                    line.systemQuantity = system
                    val diff = qty - system
                    line.cost = when {
                        diff.signum() < 0 -> ledger.issue(doc.warehouseId, l.stockItemId, diff.negate(), "COUNT_LOSS", source).cost.negate()
                        diff.signum() > 0 -> {
                            val cost = ledger.lastUnitCost(doc.warehouseId, l.stockItemId)
                            ledger.receive(doc.warehouseId, l.stockItemId, diff, cost, clock.instant(), "COUNT_GAIN", source)
                            diff * cost
                        }
                        else -> BigDecimal.ZERO
                    }
                }
            }
            doc.lines.add(line)
            total += line.cost
        }
        doc.totalCost = total
        return doc.toDto()
    }

    private fun positive(qty: BigDecimal) = qty.takeIf { it.signum() > 0 } ?: throw UnprocessableException("inventory.quantity_positive")

    // ---------------------------------------------------------------- recipes
    fun productRecipe(productId: UUID): Recipe = recipe(recipes.findAllByProductIdOrderByPosition(productId)).copy(productId = productId)

    fun optionRecipe(optionId: UUID): Recipe = recipe(recipes.findAllByOptionIdOrderByPosition(optionId)).copy(optionId = optionId)

    @Transactional
    fun replaceProductRecipe(productId: UUID, req: RecipeRequest): Recipe {
        recipes.deleteAllByProductId(productId)
        recipes.flush()
        recipes.saveAll(toLines(req) { item, qty, wh, pos -> RecipeLineEntity(productId = productId, stockItemId = item, quantity = qty, warehouseId = wh, position = pos) })
        return productRecipe(productId)
    }

    @Transactional
    fun replaceOptionRecipe(optionId: UUID, req: RecipeRequest): Recipe {
        recipes.deleteAllByOptionId(optionId)
        recipes.flush()
        recipes.saveAll(toLines(req) { item, qty, wh, pos -> RecipeLineEntity(optionId = optionId, stockItemId = item, quantity = qty, warehouseId = wh, position = pos) })
        return optionRecipe(optionId)
    }

    private fun toLines(req: RecipeRequest, build: (UUID, BigDecimal, UUID?, Int) -> RecipeLineEntity) =
        req.lines.mapIndexed { i, l ->
            l.warehouseId?.let { warehouses.findByIdOrNull(it) ?: throw UnprocessableException("catalog.invalid_reference", "Warehouse") }
            build(l.stockItemId, positive(catalog.toBase(l.stockItemId, BigDecimal(l.quantity), l.unitId, null)), l.warehouseId, i + 1)
        }

    private fun recipe(lines: List<RecipeLineEntity>): Recipe {
        val locale = LocaleContextHolder.getLocale()
        val currency = settings.currency()
        var total = BigDecimal.ZERO
        val dto = lines.map { l ->
            val item = catalog.loadItem(l.stockItemId)
            val cost = l.quantity * ledger.currentUnitCost(l.warehouseId, l.stockItemId)
            total += cost
            RecipeLine(l.stockItemId, item.name.localized(locale), l.quantity.qty(), catalog.unitCodeOf(item.baseUnitId), l.warehouseId, cost.toMoney(currency))
        }
        return Recipe(lines = dto, estimatedCost = total.toMoney(currency))
    }

    // ---------------------------------------------------------------- mapping
    private fun StockDocumentEntity.toDto(): StockDocument {
        val locale = LocaleContextHolder.getLocale()
        val currency = settings.currency()
        return StockDocument(
            id = id,
            number = number,
            type = StockDocumentTypeDto.forValue(type.name),
            date = date,
            warehouseId = warehouseId,
            lines = lines.map { l ->
                val item = catalog.loadItem(l.stockItemId)
                StockDocumentLine(
                    stockItemId = l.stockItemId,
                    itemName = item.name.localized(locale),
                    quantity = l.quantity.qty(),
                    unitCode = catalog.unitCodeOf(item.baseUnitId),
                    systemQuantity = l.systemQuantity?.qty(),
                    difference = l.systemQuantity?.let { (l.quantity - it).qty() },
                    cost = l.cost.toMoney(currency),
                    reason = l.reason,
                )
            },
            totalCost = totalCost.toMoney(currency),
            createdAt = createdAt.atOffset(ZoneOffset.UTC),
            targetWarehouseId = targetWarehouseId,
            note = note,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun jsonName(value: String): Map<String, String> = json.readValue(value, Map::class.java) as Map<String, String>
}
