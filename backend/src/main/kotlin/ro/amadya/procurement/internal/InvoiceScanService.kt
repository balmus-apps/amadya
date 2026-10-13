package ro.amadya.procurement.internal

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import ro.amadya.contract.model.InvoiceDocType
import ro.amadya.contract.model.InvoiceDraft
import ro.amadya.contract.model.InvoiceDraftLineInfoInner
import ro.amadya.contract.model.InvoiceDraftTotals
import ro.amadya.contract.model.InvoiceDraftWarning
import ro.amadya.contract.model.InvoicePaymentMethod
import ro.amadya.contract.model.PurchaseInvoiceLineRequest
import ro.amadya.contract.model.PurchaseInvoiceRequest
import ro.amadya.contract.model.SupplierRequest
import ro.amadya.inventory.StockCatalog
import ro.amadya.media.PrivateFiles
import ro.amadya.shared.AmadyaProperties
import ro.amadya.shared.DomainException
import ro.amadya.shared.UnprocessableException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import ro.amadya.contract.model.InvoiceDraftWarning.Code as WarningCode

/**
 * Photo or PDF → unsaved draft (ADR 0006). Only the original file is written; units, stock items and the invoice are
 * created when the reviewed draft is saved through [ProcurementService.createInvoice].
 */
@Service
class InvoiceScanService(
    private val extractor: ObjectProvider<InvoiceExtractor>,
    private val props: InvoiceScanProperties,
    private val files: PrivateFiles,
    private val nameRules: ProductNameRules,
    private val suppliers: SupplierRepository,
    private val supplierItems: SupplierItemRepository,
    private val invoices: PurchaseInvoiceRepository,
    private val stock: StockCatalog,
    private val messages: MessageSource,
    private val amadya: AmadyaProperties,
    private val clock: Clock,
) {
    fun enabled(): Boolean = extractor.ifAvailable != null

    fun scan(file: MultipartFile): InvoiceDraft {
        val reader = extractor.ifAvailable ?: throw DomainException(HttpStatus.NOT_FOUND, "procurement.scan_disabled")
        if (file.isEmpty || file.size > props.maxBytes) throw UnprocessableException("procurement.scan_too_large", props.maxBytes / 1024 / 1024)
        val stored = files.store(file.bytes)
        val extraction = try {
            reader.extract(file.bytes, stored.contentType)
        } catch (e: ExtractionFailed) {
            throw DomainException(HttpStatus.BAD_GATEWAY, "procurement.scan_failed", e.reason)
        }
        return draft(extraction, stored.key)
    }

    /** Builds the draft: supplier lookup, name rules, item matching, units, default warehouses and cross-checks. */
    fun draft(extraction: Extraction, fileKey: String?): InvoiceDraft {
        val x = extraction.invoice
        val warnings = Warnings()

        val cui = x.supplier?.cui?.takeIf { it.isNotBlank() }?.let(EFacturaParser::normalizeCui)
        val supplier = cui?.let { suppliers.findByCui(it) }
        if (cui != null && !validCui(cui)) warnings.add(WarningCode.CUI_INVALID, "draft.cui_invalid", cui)

        val name = nameRules.matcher()
        val info = mutableListOf<InvoiceDraftLineInfoInner>()
        val lines = x.lines.filter { !it.description.isNullOrBlank() }.mapIndexed { i, l ->
            val position = i + 1
            val printed = l.description!!.trim()
            val named = name(printed)
            val known = supplier?.let { s -> l.code?.let { supplierItems.findBySupplierIdAndSupplierCode(s.id, it) } ?: supplierItems.findFirstBySupplierIdAndDescriptionIgnoreCase(s.id, printed) }
            val item = named.stockItemId?.let { stock.item(it) }
                ?: (if (named.ruleId != null) stock.findItemByName(named.name) else null)
                ?: known?.let { stock.item(it.stockItemId) }
                ?: stock.findItemByName(named.name)
            val unit = l.unit?.takeIf { it.isNotBlank() }?.let { stock.findUnit(it) }
            if (l.unit != null && unit?.mapped != true && (known?.packagingId == null || known.stockItemId != item?.id)) {
                warnings.add(WarningCode.UNKNOWN_UNIT, "draft.unknown_unit", position, l.unit, line = position)
            }
            // A remembered packaging ("BU" = 430 g for this supplier's line) applies when the printed unit alone does not convert.
            val packagingId = known?.packagingId?.takeIf { item != null && known.stockItemId == item.id && (unit == null || !stock.isConvertible(item.id, unit.id, null)) }

            val qty = num(l.quantity)
            val net = num(l.net)
            val price = num(l.unitPrice) ?: if (qty != null && net != null && qty.signum() != 0) net.divide(qty, 6, RoundingMode.HALF_UP) else null
            val vatPercent = num(l.vatPercent) ?: num(l.vat)?.let { v -> net?.takeIf { it.signum() != 0 }?.let { v.multiply(BigDecimal(100)).divide(it, 0, RoundingMode.HALF_UP) } }
            if (qty != null && price != null && net != null) {
                val computed = (qty * price).setScale(2, RoundingMode.HALF_UP)
                if ((computed - net).abs() > TOLERANCE) warnings.add(WarningCode.LINE_TOTAL, "draft.line_total", position, plain(qty), plain(price), money(computed), money(net), line = position)
            }
            info += InvoiceDraftLineInfoInner(named.ruleId, l.unit)
            PurchaseInvoiceLineRequest(
                description = item?.name?.ro ?: named.name,
                quantity = qty?.let(::plain) ?: "",
                unitPrice = price?.let(::plain) ?: "",
                vatPercent = vatPercent?.let(::plain) ?: "",
                supplierCode = l.code?.trim()?.ifEmpty { null },
                unitCode = unit?.code ?: l.unit?.trim(),
                unitId = unit?.takeIf { it.mapped }?.id,
                packagingId = packagingId,
                stockItemId = item?.id,
                originalDescription = printed,
                lot = l.lot?.trim()?.ifEmpty { null },
                warehouseId = stock.defaultWarehouse(item?.id),
            )
        }

        crossCheck(x, lines, warnings)

        val issueDate = date(x.issueDate)
        if (issueDate == null) warnings.add(WarningCode.MISSING_FIELD, "draft.missing_field", "date")
        if (x.number.isNullOrBlank()) warnings.add(WarningCode.MISSING_FIELD, "draft.missing_field", "number")
        val series = x.series?.trim()?.ifEmpty { null }
        val number = x.number?.trim().orEmpty()
        if (supplier != null && number.isNotEmpty() && invoices.exists(supplier.id, series, number)) {
            warnings.add(WarningCode.DUPLICATE, "draft.duplicate", listOfNotNull(series, number).joinToString(" "))
        }
        val paid = x.payments.mapNotNull { num(it.amount) }.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)

        val invoice = PurchaseInvoiceRequest(
            number = number,
            issueDate = issueDate ?: LocalDate.now(clock.withZone(amadya.timezone)),
            lines = lines,
            supplierId = supplier?.id,
            supplier = x.supplier?.let { p ->
                SupplierRequest(
                    name = p.name?.trim().orEmpty().ifEmpty { supplier?.name.orEmpty() }, cui = cui, regCom = p.regCom?.trim(), address = p.address?.trim(),
                    iban = p.iban?.replace(" ", "")?.uppercase(), phone = p.phone?.trim(),
                )
            },
            series = series,
            dueDate = date(x.dueDate),
            currency = x.currency?.trim()?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{3}")) } ?: "RON",
            docType = x.docType?.let { t -> InvoiceDocType.entries.firstOrNull { it.value == t } },
            issueTime = time(x.issueTime),
            supplierBranch = x.supplierBranch?.trim()?.ifEmpty { null },
            clientNo = x.clientNo?.trim()?.ifEmpty { null },
            cardNo = x.cardNo?.trim()?.ifEmpty { null },
            registerNo = x.registerNo?.trim()?.ifEmpty { null },
            cashierNo = x.cashierNo?.trim()?.ifEmpty { null },
            buyerName = x.buyer?.name?.trim()?.ifEmpty { null },
            buyerCui = x.buyer?.cui?.trim()?.ifEmpty { null },
            buyerPerson = x.buyerPerson?.trim()?.ifEmpty { null },
            paymentMethod = x.payments.firstNotNullOfOrNull { p -> InvoicePaymentMethod.entries.firstOrNull { it.value == p.method?.uppercase() } },
            paidAmount = paid?.let(::money),
            discountTotal = num(x.discountTotal)?.let(::money),
            fileKey = fileKey,
            rawExtraction = extraction.raw,
            createNirs = true,
        )
        val printed = InvoiceDraftTotals(num(x.totalNet)?.let(::money), num(x.totalVat)?.let(::money), num(x.totalGross)?.let(::money))
        return InvoiceDraft(invoice, printed, warnings.list, info)
    }

    /** VAT per rate and the document total, as computed from the lines, against what is printed. */
    private fun crossCheck(x: ExtractedInvoice, lines: List<PurchaseInvoiceLineRequest>, warnings: Warnings) {
        val byRate = sortedMapOf<BigDecimal, Pair<BigDecimal, BigDecimal>>()
        for (l in lines) {
            val qty = l.quantity.toBigDecimalOrNull() ?: return
            val price = l.unitPrice.toBigDecimalOrNull() ?: return
            val rate = l.vatPercent.toBigDecimalOrNull() ?: return
            val net = (qty * price).setScale(2, RoundingMode.HALF_UP)
            val (n, v) = byRate[rate.stripTrailingZeros()] ?: (BigDecimal.ZERO to BigDecimal.ZERO)
            byRate[rate.stripTrailingZeros()] = (n + net) to (v + vatOf(net, rate))
        }
        for (printed in x.vatBreakdown) {
            val rate = num(printed.percent)?.stripTrailingZeros() ?: continue
            val printedVat = num(printed.vat) ?: continue
            val computed = byRate[rate]?.second ?: BigDecimal.ZERO
            if ((computed - printedVat).abs() > TOLERANCE) warnings.add(WarningCode.VAT_TOTAL, "draft.vat_total", plain(rate), money(computed), money(printedVat))
        }
        val gross = byRate.values.fold(BigDecimal.ZERO) { a, (n, v) -> a + n + v }
        val printedGross = num(x.totalGross)
        if (printedGross != null && (gross - printedGross).abs() > TOLERANCE) {
            warnings.add(WarningCode.DOCUMENT_TOTAL, "draft.document_total", money(gross), money(printedGross))
        }
    }

    /** Warnings in the caller's language; they never block saving. */
    private inner class Warnings {
        val list = mutableListOf<InvoiceDraftWarning>()

        fun add(code: WarningCode, key: String, vararg args: Any?, line: Int? = null) {
            list += InvoiceDraftWarning(code, messages.getMessage(key, args.map { it ?: "" }.toTypedArray(), key, LocaleContextHolder.getLocale()) ?: key, line)
        }
    }

    companion object {
        private val TOLERANCE = BigDecimal("0.02")

        fun vatOf(net: BigDecimal, percent: BigDecimal): BigDecimal = net.multiply(percent).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

        fun plain(v: BigDecimal): String = v.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()

        fun money(v: BigDecimal): String = v.setScale(2, RoundingMode.HALF_UP).toPlainString()

        fun date(s: String?): LocalDate? = s?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        fun time(s: String?): String? = s?.trim()?.let { runCatching { LocalTime.parse(if (it.length == 4) "0$it" else it.take(5)) }.getOrNull() }
            ?.let { "%02d:%02d".format(it.hour, it.minute) }

        /** Romanian CUI check digit (key 753217532). */
        fun validCui(cui: String): Boolean {
            val digits = cui.removePrefix("RO").removePrefix("ro").trim()
            if (!digits.matches(Regex("\\d{2,10}"))) return false
            val body = digits.dropLast(1).padStart(9, '0')
            val sum = body.zip("753217532").sumOf { (d, k) -> (d - '0') * (k - '0') }
            val control = (sum * 10 % 11).let { if (it == 10) 0 else it }
            return control == digits.last() - '0'
        }
    }
}
