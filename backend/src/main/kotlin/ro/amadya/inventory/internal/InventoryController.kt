package ro.amadya.inventory.internal

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.InventoryApi
import ro.amadya.contract.model.MeasureUnit
import ro.amadya.contract.model.MeasureUnitRequest
import ro.amadya.contract.model.Recipe
import ro.amadya.contract.model.RecipeRequest
import ro.amadya.contract.model.StockBalance
import ro.amadya.contract.model.StockDocument
import ro.amadya.contract.model.StockDocumentRequest
import ro.amadya.contract.model.StockDocumentType
import ro.amadya.contract.model.StockItem
import ro.amadya.contract.model.StockItemRequest
import ro.amadya.contract.model.StockLot
import ro.amadya.contract.model.StockMovement
import ro.amadya.contract.model.Warehouse
import ro.amadya.contract.model.WarehouseRequest
import ro.amadya.contract.model.WarehouseType
import java.util.UUID

@RestController
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
class InventoryController(private val catalog: StockCatalogService, private val stock: StockService) : InventoryApi {

    override fun listMeasureUnits(): ResponseEntity<List<MeasureUnit>> = ResponseEntity.ok(catalog.listUnits())

    override fun createMeasureUnit(measureUnitRequest: MeasureUnitRequest): ResponseEntity<MeasureUnit> =
        ResponseEntity.status(HttpStatus.CREATED).body(catalog.createUnit(measureUnitRequest))

    override fun updateMeasureUnit(id: UUID, measureUnitRequest: MeasureUnitRequest): ResponseEntity<MeasureUnit> =
        ResponseEntity.ok(catalog.updateUnit(id, measureUnitRequest))

    override fun listWarehouses(): ResponseEntity<List<Warehouse>> = ResponseEntity.ok(catalog.listWarehouses())

    override fun createWarehouse(warehouseRequest: WarehouseRequest): ResponseEntity<Warehouse> =
        ResponseEntity.status(HttpStatus.CREATED).body(catalog.createWarehouse(warehouseRequest))

    override fun updateWarehouse(id: UUID, warehouseRequest: WarehouseRequest): ResponseEntity<Warehouse> =
        ResponseEntity.ok(catalog.updateWarehouse(id, warehouseRequest))

    override fun listStockItems(type: WarehouseType?, q: String?): ResponseEntity<List<StockItem>> = ResponseEntity.ok(catalog.listItems(type, q))

    override fun getStockItem(id: UUID): ResponseEntity<StockItem> = ResponseEntity.ok(catalog.getItem(id))

    override fun createStockItem(stockItemRequest: StockItemRequest): ResponseEntity<StockItem> =
        ResponseEntity.status(HttpStatus.CREATED).body(catalog.createItem(stockItemRequest))

    override fun updateStockItem(id: UUID, stockItemRequest: StockItemRequest): ResponseEntity<StockItem> =
        ResponseEntity.ok(catalog.updateItem(id, stockItemRequest))

    override fun listStockBalances(warehouseId: UUID?, lowOnly: Boolean): ResponseEntity<List<StockBalance>> =
        ResponseEntity.ok(stock.balances(warehouseId, lowOnly))

    override fun listStockMovements(warehouseId: UUID?, stockItemId: UUID?, limit: Int): ResponseEntity<List<StockMovement>> =
        ResponseEntity.ok(stock.movements(warehouseId, stockItemId, limit))

    override fun listStockLots(warehouseId: UUID?, stockItemId: UUID?): ResponseEntity<List<StockLot>> =
        ResponseEntity.ok(stock.lots(warehouseId, stockItemId))

    override fun listStockDocuments(type: StockDocumentType?, limit: Int): ResponseEntity<List<StockDocument>> =
        ResponseEntity.ok(stock.listDocuments(type, limit))

    override fun getStockDocument(id: UUID): ResponseEntity<StockDocument> = ResponseEntity.ok(stock.getDocument(id))

    override fun createStockDocument(stockDocumentRequest: StockDocumentRequest): ResponseEntity<StockDocument> =
        ResponseEntity.status(HttpStatus.CREATED).body(stock.createDocument(stockDocumentRequest))

    override fun getProductRecipe(productId: UUID): ResponseEntity<Recipe> = ResponseEntity.ok(stock.productRecipe(productId))

    override fun replaceProductRecipe(productId: UUID, recipeRequest: RecipeRequest): ResponseEntity<Recipe> =
        ResponseEntity.ok(stock.replaceProductRecipe(productId, recipeRequest))

    override fun getOptionRecipe(optionId: UUID): ResponseEntity<Recipe> = ResponseEntity.ok(stock.optionRecipe(optionId))

    override fun replaceOptionRecipe(optionId: UUID, recipeRequest: RecipeRequest): ResponseEntity<Recipe> =
        ResponseEntity.ok(stock.replaceOptionRecipe(optionId, recipeRequest))
}
