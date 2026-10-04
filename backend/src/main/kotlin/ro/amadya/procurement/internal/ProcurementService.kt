package ro.amadya.procurement.internal

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Limit
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.contract.model.InvoiceLineMatchRequest
import ro.amadya.contract.model.Nir
import ro.amadya.contract.model.NirLine
import ro.amadya.contract.model.NirRequest
import ro.amadya.contract.model.PurchaseInvoice
import ro.amadya.contract.model.PurchaseInvoiceLine
import ro.amadya.contract.model.PurchaseInvoiceRequest
import ro.amadya.contract.model.Supplier
import ro.amadya.contract.model.SupplierRequest
import ro.amadya.inventory.Receipt
import ro.amadya.inventory.StockCatalog
import ro.amadya.inventory.StockLedger
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.Actor
import ro.amadya.shared.AmadyaProperties
import ro.amadya.shared.ConflictException
import ro.amadya.shared.DocumentNumbers
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.UnprocessableException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import ro.amadya.contract.model.InvoiceSource as InvoiceSourceDto
import ro.amadya.contract.model.NirStatus as NirStatusDto

@Service
@Transactional(readOnly = true)
class ProcurementService(
    private val suppliers: SupplierRepository,
    private val supplierItems: SupplierItemRepository,
    private val invoices: PurchaseInvoiceRepository,
    private val nirs: NirRepository,
    private val stock: StockCatalog,
    private val ledger: StockLedger,
    private val numbers: DocumentNumbers,
    private val settings: RestaurantSettingsApi,
    private val props: AmadyaProperties,
    private val clock: Clock,
) {
    // ---------------------------------------------------------------- suppliers
    fun listSuppliers(): List<Supplier> = suppliers.findAllByOrderByNameAsc().map { it.toDto() }

    @Transactional
    fun createSupplier(req: SupplierRequest): Supplier {
        val cui = req.cui?.takeIf { it.isNotBlank() }?.let(EFacturaParser::normalizeCui)
        if (cui != null && suppliers.findByCui(cui) != null) throw ConflictException("procurement.supplier_exists", cui)
        return suppliers.save(SupplierEntity(name = req.name.trim()).also { apply(it, req, cui) }).toDto()
    }

    @Transactional
    fun updateSupplier(id: UUID, req: SupplierRequest): Supplier {
        val supplier = suppliers.findByIdOrNull(id) ?: throw NotFoundException("Supplier")
        val cui = req.cui?.takeIf { it.isNotBlank() }?.let(EFacturaParser::normalizeCui)
        if (cui != null && cui != supplier.cui && suppliers.findByCui(cui) != null) throw ConflictException("procurement.supplier_exists", cui)
        apply(supplier, req, cui)
        return supplier.toDto()
    }

    private fun apply(s: SupplierEntity, req: SupplierRequest, cui: String?) {
        s.name = req.name.trim()
        s.cui = cui
        s.regCom = req.regCom
        s.address = req.address
        s.iban = req.iban?.replace(" ", "")?.uppercase()
        s.email = req.email
        s.phone = req.phone
        s.active = req.active ?: true
    }

    // ---------------------------------------------------------------- invoices
    fun listInvoices(limit: Int): List<PurchaseInvoice> = invoices.recent(Limit.of(limit)).map { it.toDto() }

    fun getInvoice(id: UUID): PurchaseInvoice = loadInvoice(id).toDto()

    @Transactional
    fun createInvoice(req: PurchaseInvoiceRequest): PurchaseInvoice {
        val supplier = suppliers.findByIdOrNull(req.supplierId) ?: throw UnprocessableException("catalog.invalid_reference", "Supplier")
        if (invoices.exists(supplier.id, req.series, req.number)) throw ConflictException("procurement.invoice_exists", req.number)
        val invoice = PurchaseInvoiceEntity(
            supplierId = supplier.id, series = req.series, number = req.number.trim(), issueDate = req.issueDate, dueDate = req.dueDate,
            currency = req.currency ?: "RON", source = InvoiceSource.MANUAL,
        )
        req.lines.forEachIndexed { i, l ->
            val qty = BigDecimal(l.quantity)
            val price = BigDecimal(l.unitPrice)
            val unitId = l.unitId ?: l.unitCode?.let { stock.resolveUnit(it).id }
            invoice.lines += InvoiceLineEntity(
                position = i + 1, supplierCode = l.supplierCode, description = l.description.trim(), quantity = qty,
                unitCode = l.unitCode ?: unitId?.let { stock.unitCode(it, null) }, unitId = unitId, packagingId = l.packagingId,
                unitPrice = price, vatPercent = BigDecimal(l.vatPercent), lineNet = (qty * price).setScale(2, RoundingMode.HALF_UP),
                stockItemId = l.stockItemId,
            )
        }
        autoMatch(supplier.id, invoice)
        totals(invoice)
        return invoices.save(invoice).toDto()
    }

    /** Imports an e-Factura XML: finds or creates the supplier by CUI, resolves units and matches lines from remembered mappings. */
    @Transactional
    fun importEFactura(xml: String): PurchaseInvoice {
        val parsed = EFacturaParser.parse(xml)
        val supplier = parsed.supplierCui?.let { suppliers.findByCui(it) }
            ?: suppliers.save(SupplierEntity(name = parsed.supplierName, cui = parsed.supplierCui, regCom = parsed.supplierRegCom, address = parsed.supplierAddress))
        if (invoices.exists(supplier.id, parsed.series, parsed.number)) throw ConflictException("procurement.invoice_exists", parsed.number)
        val invoice = PurchaseInvoiceEntity(
            supplierId = supplier.id, series = parsed.series, number = parsed.number, issueDate = parsed.issueDate, dueDate = parsed.dueDate,
            currency = parsed.currency, source = InvoiceSource.EFACTURA_XML, raw = xml,
        )
        parsed.lines.forEachIndexed { i, l ->
            invoice.lines += InvoiceLineEntity(
                position = i + 1, supplierCode = l.supplierCode, description = l.description, quantity = l.quantity, unitCode = l.unitCode,
                unitId = l.unitCode?.let { stock.resolveUnit(it).id }, unitPrice = l.unitPrice, vatPercent = l.vatPercent, lineNet = l.lineNet,
            )
        }
        autoMatch(supplier.id, invoice)
        totals(invoice)
        return invoices.save(invoice).toDto()
    }

    @Transactional
    fun matchLine(invoiceId: UUID, lineId: UUID, req: InvoiceLineMatchRequest): PurchaseInvoice {
        val invoice = loadInvoice(invoiceId)
        val line = invoice.lines.firstOrNull { it.id == lineId } ?: throw NotFoundException("Invoice line")
        stock.item(req.stockItemId) ?: throw UnprocessableException("catalog.invalid_reference", "Stock item")
        line.stockItemId = req.stockItemId
        req.unitId?.let { line.unitId = it }
        line.packagingId = req.packagingId
        if (!stock.isConvertible(req.stockItemId, line.unitId, line.packagingId)) {
            throw UnprocessableException("inventory.unit_not_convertible", line.unitCode ?: "?", stock.item(req.stockItemId)!!.sku)
        }
        if (req.remember != false) remember(invoice.supplierId, line)
        return invoice.toDto()
    }

    private fun autoMatch(supplierId: UUID, invoice: PurchaseInvoiceEntity) {
        for (line in invoice.lines.filter { it.stockItemId == null }) {
            val known = line.supplierCode?.let { supplierItems.findBySupplierIdAndSupplierCode(supplierId, it) }
                ?: supplierItems.findFirstBySupplierIdAndDescriptionIgnoreCase(supplierId, line.description)
            if (known != null) {
                line.stockItemId = known.stockItemId
                line.packagingId = known.packagingId
                known.unitId?.let { line.unitId = it }
            } else {
                stock.findItemByName(line.description)?.let { line.stockItemId = it.id }
            }
        }
    }

    private fun remember(supplierId: UUID, line: InvoiceLineEntity) {
        val existing = line.supplierCode?.let { supplierItems.findBySupplierIdAndSupplierCode(supplierId, it) }
            ?: supplierItems.findFirstBySupplierIdAndDescriptionIgnoreCase(supplierId, line.description)
        val mapping = existing ?: SupplierItemEntity(supplierId = supplierId, supplierCode = line.supplierCode, description = line.description, stockItemId = line.stockItemId!!, unitId = line.unitId, packagingId = line.packagingId)
        mapping.stockItemId = line.stockItemId!!
        mapping.unitId = line.unitId
        mapping.packagingId = line.packagingId
        mapping.updatedAt = clock.instant()
        supplierItems.save(mapping)
    }

    private fun totals(invoice: PurchaseInvoiceEntity) {
        invoice.totalNet = invoice.lines.fold(BigDecimal.ZERO) { a, l -> a + l.lineNet }.setScale(2, RoundingMode.HALF_UP)
        invoice.totalVat = invoice.lines.fold(BigDecimal.ZERO) { a, l -> a + vat(l.lineNet, l.vatPercent) }.setScale(2, RoundingMode.HALF_UP)
        invoice.totalGross = invoice.totalNet + invoice.totalVat
    }

    private fun loadInvoice(id: UUID) = invoices.findByIdOrNull(id) ?: throw NotFoundException("Invoice")

    // ---------------------------------------------------------------- NIR
    fun listNirs(status: NirStatusDto?, limit: Int): List<Nir> = nirs.search(status?.let { NirStatus.valueOf(it.value) }, Limit.of(limit)).map { it.toDto() }

    fun getNir(id: UUID): Nir = loadNir(id).toDto()

    @Transactional
    fun createNir(req: NirRequest): Nir {
        stock.warehouse(req.warehouseId) ?: throw UnprocessableException("catalog.invalid_reference", "Warehouse")
        val today = LocalDate.now(clock.withZone(props.timezone))
        val invoice = req.invoiceId?.let { loadInvoice(it) }
        if (invoice != null && nirs.findFirstByInvoiceIdAndReversalOfIdIsNull(invoice.id) != null) throw ConflictException("procurement.invoice_has_nir")
        val nir = NirEntity(date = req.date ?: today, warehouseId = req.warehouseId, invoiceId = invoice?.id)
        applyHeader(nir, req)
        if (invoice != null) {
            val unmatched = invoice.lines.filter { it.stockItemId == null || !stock.isConvertible(it.stockItemId!!, it.unitId, it.packagingId) }
            if (unmatched.isNotEmpty()) throw UnprocessableException("procurement.invoice_unmatched", unmatched.joinToString { it.description })
            nir.supplierId = invoice.supplierId
            nir.invoiceRef = nir.invoiceRef ?: listOfNotNull(invoice.series?.takeIf { !invoice.number.startsWith(it) }, invoice.number).joinToString(" ") + " / " + invoice.issueDate
            invoice.lines.forEach { l ->
                nir.lines += NirLineEntity(
                    position = l.position, stockItemId = l.stockItemId!!, unitId = l.unitId, packagingId = l.packagingId,
                    qtyDocument = l.quantity, qtyReceived = l.quantity, unitPrice = l.unitPrice, vatPercent = l.vatPercent, discrepancyReason = null,
                )
            }
        } else {
            replaceLines(nir, req)
        }
        return nirs.save(nir).toDto()
    }

    @Transactional
    fun updateNir(id: UUID, req: NirRequest): Nir {
        val nir = loadNir(id)
        if (nir.status != NirStatus.DRAFT) throw ConflictException("procurement.nir_not_draft")
        stock.warehouse(req.warehouseId) ?: throw UnprocessableException("catalog.invalid_reference", "Warehouse")
        nir.warehouseId = req.warehouseId
        req.date?.let { nir.date = it }
        applyHeader(nir, req)
        if (req.lines != null) replaceLines(nir, req)
        return nir.toDto()
    }

    /** Posting receives every line as a FIFO lot at its net cost per base unit; the NIR is then immutable. */
    @Transactional
    fun postNir(id: UUID): Nir {
        val nir = loadNir(id)
        if (nir.status != NirStatus.DRAFT) throw ConflictException("procurement.nir_not_draft")
        if (nir.lines.isEmpty()) throw UnprocessableException("procurement.nir_empty")
        nir.number = numbers.next("NIR", nir.date.year)
        val receivedAt = nir.date.atStartOfDay(props.timezone).toInstant().coerceAtMost(clock.instant())
        for (line in nir.lines) {
            if (line.qtyReceived.signum() <= 0) continue
            val baseQty = stock.toBase(line.stockItemId, line.qtyReceived, line.unitId, line.packagingId)
            val unitCost = (line.qtyReceived * line.unitPrice).divide(baseQty, 6, RoundingMode.HALF_UP)
            ledger.receive(Receipt(nir.warehouseId, line.stockItemId, baseQty, unitCost, receivedAt, "NIR", nir.id, nir.number!!))
        }
        nir.status = NirStatus.POSTED
        nir.postedAt = clock.instant()
        nir.postedBy = Actor.current()?.userId
        return nir.toDto()
    }

    /** Counter-document: posted with negative quantities, while the original lots are still untouched. */
    @Transactional
    fun reverseNir(id: UUID): Nir {
        val original = loadNir(id)
        if (original.status != NirStatus.POSTED || original.reversalOfId != null) throw ConflictException("procurement.nir_not_reversible")
        val today = LocalDate.now(clock.withZone(props.timezone))
        val reversal = NirEntity(
            number = numbers.next("NIR", today.year), status = NirStatus.POSTED, date = today, warehouseId = original.warehouseId,
            supplierId = original.supplierId, invoiceRef = original.invoiceRef, deliveryNoteRef = original.deliveryNoteRef,
            committee = original.committee.toMutableList(), notes = "Stornare ${original.number}", reversalOfId = original.id,
            postedAt = clock.instant(), postedBy = Actor.current()?.userId,
        )
        original.lines.forEach { l ->
            reversal.lines += NirLineEntity(
                position = l.position, stockItemId = l.stockItemId, unitId = l.unitId, packagingId = l.packagingId,
                qtyDocument = l.qtyDocument.negate(), qtyReceived = l.qtyReceived.negate(), unitPrice = l.unitPrice, vatPercent = l.vatPercent,
                discrepancyReason = l.discrepancyReason,
            )
        }
        ledger.reverseReceipts("NIR", original.id, reversal.number!!)
        original.status = NirStatus.REVERSED
        return nirs.save(reversal).toDto()
    }

    private fun applyHeader(nir: NirEntity, req: NirRequest) {
        req.supplierId?.let { suppliers.findByIdOrNull(it) ?: throw UnprocessableException("catalog.invalid_reference", "Supplier"); nir.supplierId = it }
        req.invoiceRef?.let { nir.invoiceRef = it.ifBlank { null } }
        req.deliveryNoteRef?.let { nir.deliveryNoteRef = it.ifBlank { null } }
        req.committee?.let { nir.committee = it.map(String::trim).filter(String::isNotEmpty).toMutableList() }
        req.notes?.let { nir.notes = it.ifBlank { null } }
    }

    private fun replaceLines(nir: NirEntity, req: NirRequest) {
        nir.lines.clear()
        req.lines.orEmpty().forEachIndexed { i, l ->
            if (!stock.isConvertible(l.stockItemId, l.unitId, l.packagingId)) {
                throw UnprocessableException("inventory.unit_not_convertible", stock.unitCode(l.unitId, l.packagingId) ?: "?", stock.item(l.stockItemId)?.sku ?: l.stockItemId)
            }
            val qtyDoc = BigDecimal(l.quantityDocument)
            val qtyRec = BigDecimal(l.quantityReceived)
            if (qtyRec.compareTo(qtyDoc) != 0 && l.discrepancyReason.isNullOrBlank()) throw UnprocessableException("procurement.discrepancy_reason_required", i + 1)
            nir.lines += NirLineEntity(
                position = i + 1, stockItemId = l.stockItemId, unitId = l.unitId, packagingId = l.packagingId, qtyDocument = qtyDoc, qtyReceived = qtyRec,
                unitPrice = BigDecimal(l.unitPrice), vatPercent = l.vatPercent?.let(::BigDecimal) ?: BigDecimal.ZERO, discrepancyReason = l.discrepancyReason,
            )
        }
    }

    private fun loadNir(id: UUID) = nirs.findByIdOrNull(id) ?: throw NotFoundException("NIR")

    // ---------------------------------------------------------------- mapping
    private fun vat(net: BigDecimal, percent: BigDecimal) = net.multiply(percent).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

    private fun money(v: BigDecimal) = v.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun SupplierEntity.toDto() = Supplier(id, name, active, cui, regCom, address, iban, email, phone)

    private fun PurchaseInvoiceEntity.toDto(): PurchaseInvoice {
        val supplier = suppliers.findByIdOrNull(supplierId)
        return PurchaseInvoice(
            id = id, supplierId = supplierId, supplierName = supplier?.name ?: "?", number = number, issueDate = issueDate, currency = currency,
            totalNet = money(totalNet), totalVat = money(totalVat), totalGross = money(totalGross), source = InvoiceSourceDto.forValue(source.name),
            lines = lines.map { l ->
                PurchaseInvoiceLine(
                    id = l.id, position = l.position, description = l.description, quantity = l.quantity.setScale(3, RoundingMode.HALF_UP).toPlainString(),
                    unitCode = l.unitCode ?: "", unitPrice = l.unitPrice.stripTrailingZeros().toPlainString(), vatPercent = l.vatPercent.toPlainString(),
                    lineNet = money(l.lineNet), supplierCode = l.supplierCode, unitId = l.unitId, packagingId = l.packagingId, stockItemId = l.stockItemId,
                    matched = l.stockItemId != null && stock.isConvertible(l.stockItemId!!, l.unitId, l.packagingId),
                )
            },
            series = series, dueDate = dueDate, nirId = nirs.findFirstByInvoiceIdAndReversalOfIdIsNull(id)?.id,
        )
    }

    private fun NirEntity.toDto(): Nir {
        val locale = LocaleContextHolder.getLocale()
        val reversedBy = if (status == NirStatus.REVERSED) nirs.findFirstByReversalOfId(id)?.id else null
        var net = BigDecimal.ZERO
        var vatTotal = BigDecimal.ZERO
        val dtoLines = lines.map { l ->
            val item = stock.item(l.stockItemId)
            val valueNet = (l.qtyReceived * l.unitPrice).setScale(2, RoundingMode.HALF_UP)
            val v = vat(valueNet, l.vatPercent)
            net += valueNet
            vatTotal += v
            NirLine(
                id = l.id, position = l.position, stockItemId = l.stockItemId, itemName = item?.name?.resolve(locale) ?: "?",
                unitCode = stock.unitCode(l.unitId, l.packagingId) ?: item?.baseUnitCode ?: "",
                quantityDocument = l.qtyDocument.setScale(3).toPlainString(), quantityReceived = l.qtyReceived.setScale(3).toPlainString(),
                difference = (l.qtyReceived - l.qtyDocument).setScale(3).toPlainString(), unitPrice = l.unitPrice.stripTrailingZeros().toPlainString(),
                vatPercent = l.vatPercent.toPlainString(), valueNet = money(valueNet), vatValue = money(v),
                unitId = l.unitId, packagingId = l.packagingId, discrepancyReason = l.discrepancyReason,
            )
        }
        return Nir(
            id = id, status = NirStatusDto.forValue(status.name), date = date, warehouseId = warehouseId, committee = committee, lines = dtoLines,
            totalNet = money(net), totalVat = money(vatTotal), currency = settings.currency(), number = number, supplierId = supplierId,
            supplierName = supplierId?.let { suppliers.findByIdOrNull(it)?.name }, invoiceId = invoiceId, invoiceRef = invoiceRef,
            deliveryNoteRef = deliveryNoteRef, notes = notes, reversalOfId = reversalOfId, reversedById = reversedBy,
            postedAt = postedAt?.atOffset(ZoneOffset.UTC),
        )
    }
}
