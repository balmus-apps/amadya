package ro.amadya.inventory.internal

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ro.amadya.shared.BaseEntity
import ro.amadya.shared.Ids
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class Dimension { MASS, VOLUME, COUNT, LENGTH }

enum class UnitStatus { ACTIVE, UNMAPPED }

enum class WarehouseType { INGREDIENTS, FINISHED_GOODS, FIXED_ASSETS, CONSUMABLES }

enum class DocumentType(val prefix: String) { TRANSFER("TR"), CONSUMPTION("BC"), WASTE("PV"), COUNT("INV") }

@Entity
@Table(name = "unit_of_measure")
class UnitEntity(
    var code: String,
    @JdbcTypeCode(SqlTypes.JSON) var name: MutableMap<String, String>,
    @Enumerated(EnumType.STRING) var dimension: Dimension?,
    var factor: BigDecimal?,
    @Column(name = "is_reference") val isReference: Boolean = false,
    @JdbcTypeCode(SqlTypes.ARRAY) var aliases: MutableList<String> = mutableListOf(),
    @Enumerated(EnumType.STRING) var status: UnitStatus = UnitStatus.ACTIVE,
) : BaseEntity()

@Entity
@Table(name = "warehouse")
class WarehouseEntity(
    var code: String,
    @JdbcTypeCode(SqlTypes.JSON) var name: MutableMap<String, String>,
    @Enumerated(EnumType.STRING) var type: WarehouseType,
    var active: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "stock_item")
class StockItemEntity(
    var sku: String,
    @JdbcTypeCode(SqlTypes.JSON) var name: MutableMap<String, String>,
    @Enumerated(EnumType.STRING) var type: WarehouseType,
    @Column(name = "base_unit_id") var baseUnitId: UUID,
    @Column(name = "display_unit_id") var displayUnitId: UUID? = null,
    @Column(name = "default_warehouse_id") var defaultWarehouseId: UUID? = null,
    @Column(name = "min_stock") var minStock: BigDecimal = BigDecimal.ZERO,
    var active: Boolean = true,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "stock_item_id", nullable = false)
    var packagings: MutableList<PackagingEntity> = mutableListOf(),
) : BaseEntity()

@Entity
@Table(name = "stock_item_packaging")
class PackagingEntity(
    @Id val id: UUID = Ids.newId(),
    var name: String,
    @Column(name = "qty_in_base") var qtyInBase: BigDecimal,
    var barcode: String? = null,
)

@Entity
@Table(name = "recipe_line")
class RecipeLineEntity(
    @Id val id: UUID = Ids.newId(),
    @Column(name = "product_id") val productId: UUID? = null,
    @Column(name = "option_id") val optionId: UUID? = null,
    @Column(name = "stock_item_id") val stockItemId: UUID,
    val quantity: BigDecimal,
    @Column(name = "warehouse_id") val warehouseId: UUID? = null,
    val position: Int = 0,
)

@Entity
@Table(name = "stock_document")
class StockDocumentEntity(
    val number: String,
    @Enumerated(EnumType.STRING) val type: DocumentType,
    @Column(name = "doc_date") val date: LocalDate,
    @Column(name = "warehouse_id") val warehouseId: UUID,
    @Column(name = "target_warehouse_id") val targetWarehouseId: UUID? = null,
    val note: String? = null,
    @Column(name = "total_cost") var totalCost: BigDecimal = BigDecimal.ZERO,
    @Column(name = "user_id") val userId: UUID? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "document_id", nullable = false)
    @OrderBy("position ASC")
    val lines: MutableList<StockDocumentLineEntity> = mutableListOf(),
) : BaseEntity()

@Entity
@Table(name = "stock_document_line")
class StockDocumentLineEntity(
    @Id val id: UUID = Ids.newId(),
    val position: Int,
    @Column(name = "stock_item_id") val stockItemId: UUID,
    var quantity: BigDecimal,
    @Column(name = "system_quantity") var systemQuantity: BigDecimal? = null,
    var cost: BigDecimal = BigDecimal.ZERO,
    val reason: String? = null,
)

interface UnitRepository : JpaRepository<UnitEntity, UUID> {
    fun findAllByOrderByDimensionAscFactorAsc(): List<UnitEntity>
    fun findByDimensionAndIsReferenceTrue(dimension: Dimension): UnitEntity?

    @Query(
        value = "SELECT * FROM unit_of_measure WHERE lower(code) = lower(:code) OR EXISTS (SELECT 1 FROM unnest(aliases) a WHERE lower(a) = lower(:code)) LIMIT 1",
        nativeQuery = true,
    )
    fun findByCodeOrAlias(code: String): UnitEntity?

    fun existsByCodeIgnoreCase(code: String): Boolean
}

interface WarehouseRepository : JpaRepository<WarehouseEntity, UUID> {
    fun findAllByOrderByCodeAsc(): List<WarehouseEntity>
    fun existsByCodeIgnoreCase(code: String): Boolean
    fun findFirstByTypeAndActiveTrueOrderByCodeAsc(type: WarehouseType): WarehouseEntity?
}

interface StockItemRepository : JpaRepository<StockItemEntity, UUID> {
    fun existsBySkuIgnoreCase(sku: String): Boolean

    @Query(
        value = """SELECT * FROM stock_item WHERE (CAST(:type AS text) IS NULL OR type = :type)
                   AND (CAST(:q AS text) IS NULL OR lower(sku) LIKE lower('%' || :q || '%') OR lower(name->>'ro') LIKE lower('%' || :q || '%')
                        OR lower(name->>'en') LIKE lower('%' || :q || '%'))
                   ORDER BY lower(name->>'ro')""",
        nativeQuery = true,
    )
    fun search(type: String?, q: String?): List<StockItemEntity>

    @Query(value = "SELECT * FROM stock_item WHERE lower(name->>'ro') = lower(:name) OR lower(name->>'en') = lower(:name) LIMIT 1", nativeQuery = true)
    fun findByName(name: String): StockItemEntity?
}

interface RecipeLineRepository : JpaRepository<RecipeLineEntity, UUID> {
    fun findAllByProductIdOrderByPosition(productId: UUID): List<RecipeLineEntity>
    fun findAllByOptionIdOrderByPosition(optionId: UUID): List<RecipeLineEntity>
    fun findAllByProductIdIn(productIds: Collection<UUID>): List<RecipeLineEntity>
    fun findAllByOptionIdIn(optionIds: Collection<UUID>): List<RecipeLineEntity>
    fun deleteAllByProductId(productId: UUID)
    fun deleteAllByOptionId(optionId: UUID)
}

interface StockDocumentRepository : JpaRepository<StockDocumentEntity, UUID> {
    @Query("SELECT d FROM StockDocumentEntity d WHERE (:type IS NULL OR d.type = :type) ORDER BY d.createdAt DESC")
    fun search(type: DocumentType?, limit: org.springframework.data.domain.Limit): List<StockDocumentEntity>
}
