package ro.amadya.procurement.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal
import java.time.Duration

/** Port from ADR 0006: reads a supplier document (photo or PDF) into structured data. A human always reviews it. */
interface InvoiceExtractor {
    /** False when the engine is configured but cannot run here (e.g. the OCR program is missing). */
    fun available(): Boolean = true

    /** [mediaType] is one of image/jpeg, image/png, image/webp, application/pdf. Throws [ExtractionFailed]. */
    fun extract(bytes: ByteArray, mediaType: String): Extraction
}

/** [raw] is a JSON record of how the document was read (engine, template, recognised text), kept for audit. */
data class Extraction(val invoice: ExtractedInvoice, val raw: String)

class ExtractionFailed(val reason: String, cause: Throwable? = null) : RuntimeException(reason, cause)

@ConfigurationProperties("amadya.invoice-scan")
data class InvoiceScanProperties(
    /** Off by default; turning it on needs the `tesseract` program (installed in the API image). */
    val enabled: Boolean = false,
    val tesseractPath: String = "tesseract",
    /** Tesseract language packs: Romanian first, English for brand names. */
    val languages: String = "ron+eng",
    val timeout: Duration = Duration.ofSeconds(60),
    val maxBytes: Long = 10 * 1024 * 1024,
    /** Longest edge photos are scaled to before OCR. */
    val maxImageEdge: Int = 2400,
)

/**
 * What the extractor reads. Numbers are strings as printed (never rounded); they are parsed with [num].
 * Every field may be null when it is not on the document.
 */
data class ExtractedInvoice(
    val docType: String? = null,
    val series: String? = null,
    val number: String? = null,
    val issueDate: String? = null,
    val issueTime: String? = null,
    val dueDate: String? = null,
    val currency: String? = null,
    val supplier: ExtractedParty? = null,
    val supplierBranch: String? = null,
    val buyer: ExtractedParty? = null,
    val buyerPerson: String? = null,
    val clientNo: String? = null,
    val cardNo: String? = null,
    val registerNo: String? = null,
    val cashierNo: String? = null,
    val lines: List<ExtractedLine> = emptyList(),
    val vatBreakdown: List<ExtractedVat> = emptyList(),
    val totalNet: String? = null,
    val totalVat: String? = null,
    val totalGross: String? = null,
    val discountTotal: String? = null,
    val payments: List<ExtractedPayment> = emptyList(),
)

data class ExtractedParty(
    val name: String? = null,
    val cui: String? = null,
    val regCom: String? = null,
    val address: String? = null,
    val iban: String? = null,
    val phone: String? = null,
)

data class ExtractedLine(
    val code: String? = null,
    val description: String? = null,
    val lot: String? = null,
    val unit: String? = null,
    val quantity: String? = null,
    val unitPrice: String? = null,
    val vatPercent: String? = null,
    val net: String? = null,
    val vat: String? = null,
    val gross: String? = null,
)

data class ExtractedVat(val percent: String? = null, val net: String? = null, val vat: String? = null, val gross: String? = null)

data class ExtractedPayment(val method: String? = null, val amount: String? = null)

/**
 * Parses a printed number: "28.240", "28,240", "1.234,56", "1,234.56", "-12,83", "150,00 RON". The last "." or ","
 * is the decimal separator; a separator that repeats with no other kind present ("1.234.567") groups thousands.
 */
fun num(value: String?): BigDecimal? {
    if (value.isNullOrBlank()) return null
    var s = value.replace(Regex("[^0-9,.\\-]"), "")
    if (s.isEmpty() || s == "-") return null
    val lastDot = s.lastIndexOf('.')
    val lastComma = s.lastIndexOf(',')
    val decimalAt = maxOf(lastDot, lastComma)
    if (decimalAt >= 0) {
        val both = lastDot >= 0 && lastComma >= 0
        val repeated = s.count { it == s[decimalAt] } > 1
        s = if (!both && repeated) {
            s.replace(s[decimalAt].toString(), "") // "1.234.567" → thousands only
        } else {
            s.substring(0, decimalAt).replace(".", "").replace(",", "") + "." + s.substring(decimalAt + 1)
        }
    }
    return s.toBigDecimalOrNull()
}
