package ro.amadya.procurement.internal

import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.ProcurementApi
import org.springframework.web.multipart.MultipartFile
import ro.amadya.contract.model.InvoiceDraft
import ro.amadya.contract.model.InvoiceLineMatchRequest
import ro.amadya.contract.model.InvoiceScanStatus
import ro.amadya.contract.model.ProductNameRule
import ro.amadya.contract.model.ProductNameRuleRequest
import ro.amadya.contract.model.ProductNameTestRequest
import ro.amadya.contract.model.ProductNameTestResult
import ro.amadya.contract.model.Nir
import ro.amadya.contract.model.NirRequest
import ro.amadya.contract.model.NirStatus
import ro.amadya.contract.model.PurchaseInvoice
import ro.amadya.contract.model.PurchaseInvoiceRequest
import ro.amadya.contract.model.Supplier
import ro.amadya.contract.model.SupplierRequest
import java.util.UUID

@RestController
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
class ProcurementController(
    private val service: ProcurementService,
    private val scans: InvoiceScanService,
    private val nameRules: ProductNameRules,
) : ProcurementApi {

    override fun getInvoiceScanStatus(): ResponseEntity<InvoiceScanStatus> = ResponseEntity.ok(InvoiceScanStatus(scans.enabled()))

    override fun scanPurchaseInvoice(file: MultipartFile): ResponseEntity<InvoiceDraft> = ResponseEntity.ok(scans.scan(file))

    override fun getPurchaseInvoiceFile(id: UUID): ResponseEntity<Resource> {
        val (type, stream) = service.invoiceFile(id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(type))
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(InputStreamResource(stream))
    }

    override fun listProductNameRules(): ResponseEntity<List<ProductNameRule>> = ResponseEntity.ok(nameRules.list())

    override fun createProductNameRule(productNameRuleRequest: ProductNameRuleRequest): ResponseEntity<ProductNameRule> =
        ResponseEntity.status(HttpStatus.CREATED).body(nameRules.create(productNameRuleRequest))

    override fun updateProductNameRule(id: UUID, productNameRuleRequest: ProductNameRuleRequest): ResponseEntity<ProductNameRule> =
        ResponseEntity.ok(nameRules.update(id, productNameRuleRequest))

    override fun deleteProductNameRule(id: UUID): ResponseEntity<Unit> {
        nameRules.delete(id)
        return ResponseEntity.noContent().build()
    }

    override fun testProductNameRule(productNameTestRequest: ProductNameTestRequest): ResponseEntity<ProductNameTestResult> =
        ResponseEntity.ok(nameRules.test(productNameTestRequest.text))

    override fun listSuppliers(): ResponseEntity<List<Supplier>> = ResponseEntity.ok(service.listSuppliers())

    override fun createSupplier(supplierRequest: SupplierRequest): ResponseEntity<Supplier> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.createSupplier(supplierRequest))

    override fun updateSupplier(id: UUID, supplierRequest: SupplierRequest): ResponseEntity<Supplier> = ResponseEntity.ok(service.updateSupplier(id, supplierRequest))

    override fun listPurchaseInvoices(limit: Int): ResponseEntity<List<PurchaseInvoice>> = ResponseEntity.ok(service.listInvoices(limit))

    override fun getPurchaseInvoice(id: UUID): ResponseEntity<PurchaseInvoice> = ResponseEntity.ok(service.getInvoice(id))

    override fun createPurchaseInvoice(purchaseInvoiceRequest: PurchaseInvoiceRequest): ResponseEntity<PurchaseInvoice> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.createInvoice(purchaseInvoiceRequest))

    override fun importEFactura(body: String): ResponseEntity<PurchaseInvoice> = ResponseEntity.status(HttpStatus.CREATED).body(service.importEFactura(body))

    override fun matchInvoiceLine(id: UUID, lineId: UUID, invoiceLineMatchRequest: InvoiceLineMatchRequest): ResponseEntity<PurchaseInvoice> =
        ResponseEntity.ok(service.matchLine(id, lineId, invoiceLineMatchRequest))

    override fun listNirs(status: NirStatus?, limit: Int): ResponseEntity<List<Nir>> = ResponseEntity.ok(service.listNirs(status, limit))

    override fun getNir(id: UUID): ResponseEntity<Nir> = ResponseEntity.ok(service.getNir(id))

    override fun createNir(nirRequest: NirRequest): ResponseEntity<Nir> = ResponseEntity.status(HttpStatus.CREATED).body(service.createNir(nirRequest))

    override fun updateNir(id: UUID, nirRequest: NirRequest): ResponseEntity<Nir> = ResponseEntity.ok(service.updateNir(id, nirRequest))

    override fun postNir(id: UUID): ResponseEntity<Nir> = ResponseEntity.ok(service.postNir(id))

    override fun reverseNir(id: UUID): ResponseEntity<Nir> = ResponseEntity.ok(service.reverseNir(id))
}
