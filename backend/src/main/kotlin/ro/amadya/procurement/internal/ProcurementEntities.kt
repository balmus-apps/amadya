package ro.amadya.procurement.internal

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
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ro.amadya.shared.BaseEntity
import ro.amadya.shared.Ids
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class InvoiceSource { MANUAL, EFACTURA_XML, SCAN }

enum class DocType { FACTURA, AVIZ, BON }

enum class PaymentMethod { CASH, CARD, TRANSFER, OTHER }

enum class NameMatchType { CONTAINS, STARTS_WITH, REGEX }

enum class NirStatus { DRAFT, POSTED, REVERSED }

@Entity
@Table(name = "supplier")
class SupplierEntity(
    var name: String,
    var cui: String? = null,
    @Column(name = "reg_com") var regCom: String? = null,
    var address: String? = null,
    var iban: String? = null,
    var email: String? = null,
    var phone: String? = null,
    var active: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "supplier_item")
class SupplierItemEntity(
    @Id val id: UUID = Ids.newId(),
    @Column(name = "supplier_id") val supplierId: UUID,
    @Column(name = "supplier_code") val supplierCode: String?,
    var description: String,
    @Column(name = "stock_item_id") var stockItemId: UUID,
    @Column(name = "unit_id") var unitId: UUID?,
    @Column(name = "packaging_id") var packagingId: UUID?,
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "purchase_invoice")
class PurchaseInvoiceEntity(
    @Column(name = "supplier_id") val supplierId: UUID,
    val series: String?,
    val number: String,
    @Column(name = "issue_date") val issueDate: LocalDate,
    @Column(name = "due_date") val dueDate: LocalDate?,
    val currency: String,
    @Column(name = "total_net") var totalNet: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_vat") var totalVat: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_gross") var totalGross: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING) val source: InvoiceSource,
    val raw: String? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "invoice_id", nullable = false)
    @OrderBy("position ASC")
    val lines: MutableList<InvoiceLineEntity> = mutableListOf(),
) : BaseEntity() {
    @Enumerated(EnumType.STRING) @Column(name = "doc_type") var docType: DocType = DocType.FACTURA
    @Column(name = "issue_time") var issueTime: LocalTime? = null
    @Column(name = "supplier_branch") var supplierBranch: String? = null
    @Column(name = "client_no") var clientNo: String? = null
    @Column(name = "card_no") var cardNo: String? = null
    @Column(name = "register_no") var registerNo: String? = null
    @Column(name = "cashier_no") var cashierNo: String? = null
    @Column(name = "buyer_name") var buyerName: String? = null
    @Column(name = "buyer_cui") var buyerCui: String? = null
    @Column(name = "buyer_person") var buyerPerson: String? = null
    @Enumerated(EnumType.STRING) @Column(name = "payment_method") var paymentMethod: PaymentMethod? = null
    @Column(name = "paid_amount") var paidAmount: BigDecimal? = null
    @Column(name = "discount_total") var discountTotal: BigDecimal? = null
    @Column(name = "file_key") var fileKey: String? = null
    @ColumnTransformer(write = "?::jsonb") @Column(name = "raw_extraction", columnDefinition = "jsonb") var rawExtraction: String? = null
}

@Entity
@Table(name = "purchase_invoice_line")
class InvoiceLineEntity(
    @Id val id: UUID = Ids.newId(),
    val position: Int,
    @Column(name = "supplier_code") val supplierCode: String?,
    val description: String,
    val quantity: BigDecimal,
    @Column(name = "unit_code") val unitCode: String?,
    @Column(name = "unit_id") var unitId: UUID?,
    @Column(name = "packaging_id") var packagingId: UUID? = null,
    @Column(name = "unit_price") val unitPrice: BigDecimal,
    @Column(name = "vat_percent") val vatPercent: BigDecimal,
    @Column(name = "line_net") val lineNet: BigDecimal,
    @Column(name = "stock_item_id") var stockItemId: UUID? = null,
    @Column(name = "original_description") val originalDescription: String? = null,
    val lot: String? = null,
    @Column(name = "warehouse_id") var warehouseId: UUID? = null,
    @Column(name = "line_vat") var lineVat: BigDecimal? = null,
    @Column(name = "line_gross") var lineGross: BigDecimal? = null,
)

@Entity
@Table(name = "product_name_rule")
class ProductNameRuleEntity(
    @Enumerated(EnumType.STRING) @Column(name = "match_type") var matchType: NameMatchType,
    var pattern: String,
    @Column(name = "target_name") var targetName: String,
    @Column(name = "stock_item_id") var stockItemId: UUID? = null,
    var priority: Int = 100,
    var active: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "nir")
class NirEntity(
    var number: String? = null,
    @Enumerated(EnumType.STRING) var status: NirStatus = NirStatus.DRAFT,
    @Column(name = "nir_date") var date: LocalDate,
    @Column(name = "warehouse_id") var warehouseId: UUID,
    @Column(name = "supplier_id") var supplierId: UUID? = null,
    @Column(name = "invoice_id") val invoiceId: UUID? = null,
    @Column(name = "invoice_ref") var invoiceRef: String? = null,
    @Column(name = "delivery_note_ref") var deliveryNoteRef: String? = null,
    @JdbcTypeCode(SqlTypes.ARRAY) var committee: MutableList<String> = mutableListOf(),
    var notes: String? = null,
    @Column(name = "reversal_of_id") val reversalOfId: UUID? = null,
    @Column(name = "posted_at") var postedAt: Instant? = null,
    @Column(name = "posted_by") var postedBy: UUID? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "nir_id", nullable = false)
    @OrderBy("position ASC")
    val lines: MutableList<NirLineEntity> = mutableListOf(),
) : BaseEntity()

@Entity
@Table(name = "nir_line")
class NirLineEntity(
    @Id val id: UUID = Ids.newId(),
    val position: Int,
    @Column(name = "stock_item_id") val stockItemId: UUID,
    @Column(name = "unit_id") val unitId: UUID?,
    @Column(name = "packaging_id") val packagingId: UUID?,
    @Column(name = "qty_document") val qtyDocument: BigDecimal,
    @Column(name = "qty_received") val qtyReceived: BigDecimal,
    @Column(name = "unit_price") val unitPrice: BigDecimal,
    @Column(name = "vat_percent") val vatPercent: BigDecimal,
    @Column(name = "discrepancy_reason") val discrepancyReason: String?,
) {
    @Generated
    @Column(name = "qty_difference", insertable = false, updatable = false)
    var qtyDifference: BigDecimal? = null
}

interface SupplierRepository : JpaRepository<SupplierEntity, UUID> {
    fun findAllByOrderByNameAsc(): List<SupplierEntity>
    fun findByCui(cui: String): SupplierEntity?
    fun findFirstByNameIgnoreCase(name: String): SupplierEntity?
}

interface SupplierItemRepository : JpaRepository<SupplierItemEntity, UUID> {
    fun findBySupplierIdAndSupplierCode(supplierId: UUID, supplierCode: String): SupplierItemEntity?
    fun findFirstBySupplierIdAndDescriptionIgnoreCase(supplierId: UUID, description: String): SupplierItemEntity?
}

interface PurchaseInvoiceRepository : JpaRepository<PurchaseInvoiceEntity, UUID> {
    @Query("SELECT i FROM PurchaseInvoiceEntity i ORDER BY i.issueDate DESC, i.createdAt DESC")
    fun recent(limit: Limit): List<PurchaseInvoiceEntity>

    @Query("SELECT count(i) > 0 FROM PurchaseInvoiceEntity i WHERE i.supplierId = :supplierId AND coalesce(i.series, '') = coalesce(:series, '') AND i.number = :number")
    fun exists(supplierId: UUID, series: String?, number: String): Boolean
}

interface NirRepository : JpaRepository<NirEntity, UUID> {
    @Query("SELECT n FROM NirEntity n WHERE (:status IS NULL OR n.status = :status) ORDER BY n.createdAt DESC")
    fun search(status: NirStatus?, limit: Limit): List<NirEntity>

    fun findFirstByInvoiceIdAndReversalOfIdIsNull(invoiceId: UUID): NirEntity?
    fun findAllByInvoiceIdAndReversalOfIdIsNullOrderByCreatedAtAsc(invoiceId: UUID): List<NirEntity>
    fun findFirstByReversalOfId(reversalOfId: UUID): NirEntity?
}

interface ProductNameRuleRepository : JpaRepository<ProductNameRuleEntity, UUID> {
    fun findAllByOrderByPriorityAscCreatedAtAsc(): List<ProductNameRuleEntity>
    fun findAllByActiveTrueOrderByPriorityAscCreatedAtAsc(): List<ProductNameRuleEntity>
}
