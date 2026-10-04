package ro.amadya.procurement.internal

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.ProcurementApi
import ro.amadya.contract.model.InvoiceLineMatchRequest
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
class ProcurementController(private val service: ProcurementService) : ProcurementApi {

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
