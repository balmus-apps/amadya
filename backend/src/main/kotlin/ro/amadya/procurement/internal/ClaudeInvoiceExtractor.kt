package ro.amadya.procurement.internal

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * ADR 0006 adapter: one Claude Messages API call per document, with a vision (image) or document (PDF) block and a
 * JSON schema enforced through structured outputs. Exists only when `amadya.invoice-scan.enabled=true`.
 */
@Component
@ConditionalOnProperty(name = ["amadya.invoice-scan.enabled"], havingValue = "true")
class ClaudeInvoiceExtractor(private val props: InvoiceScanProperties, json: JsonMapper) : InvoiceExtractor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val reader = json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()

    private val client: AnthropicClient by lazy {
        val builder = AnthropicOkHttpClient.builder().fromEnv().timeout(Duration.ofSeconds(180)).maxRetries(2)
        if (props.apiKey.isNotBlank()) builder.apiKey(props.apiKey)
        if (props.baseUrl.isNotBlank()) builder.baseUrl(props.baseUrl)
        builder.build()
    }

    override fun extract(bytes: ByteArray, mediaType: String): Extraction {
        val document = when (mediaType) {
            "application/pdf" -> ContentBlockParam.ofDocument(
                DocumentBlockParam.builder().source(Base64PdfSource.builder().data(Base64.getEncoder().encodeToString(bytes)).build()).build(),
            )
            else -> {
                val (data, type) = shrink(bytes, mediaType)
                ContentBlockParam.ofImage(
                    ImageBlockParam.builder().source(
                        Base64ImageSource.builder().data(Base64.getEncoder().encodeToString(data)).mediaType(Base64ImageSource.MediaType.of(type)).build(),
                    ).build(),
                )
            }
        }
        val params = MessageCreateParams.builder()
            .model(props.model)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).format(JsonOutputFormat.builder().schema(schema()).build()).build())
            // If a safety classifier declines, the API retries on a suitable model instead of returning a refusal.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessageOfBlockParams(listOf(document, ContentBlockParam.ofText(TextBlockParam.builder().text(USER_PROMPT).build())))
            .build()

        val message = try {
            client.messages().create(params)
        } catch (e: RateLimitException) {
            throw ExtractionFailed("rate limited, try again in a minute", e)
        } catch (e: AnthropicServiceException) {
            log.warn("Invoice extraction failed: HTTP {} {}", e.statusCode(), e.message)
            throw ExtractionFailed("service error ${e.statusCode()}", e)
        } catch (e: AnthropicIoException) {
            throw ExtractionFailed("could not reach the reading service", e)
        }
        when (message.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw ExtractionFailed("the document was declined")
            StopReason.MAX_TOKENS -> throw ExtractionFailed("the document is too long")
            else -> Unit
        }
        val raw = message.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("").trim()
        if (raw.isEmpty()) throw ExtractionFailed("empty answer")
        log.info("Invoice read with {}: {} input / {} output tokens", message.model().asString(), message.usage().inputTokens(), message.usage().outputTokens())
        return Extraction(parse(raw), raw)
    }

    fun parse(raw: String): ExtractedInvoice = try {
        reader.readValue(raw, ExtractedInvoice::class.java)
    } catch (e: Exception) {
        throw ExtractionFailed("unreadable answer", e)
    }

    /** Phone photos are often 4000 px and several MB; text stays readable at [InvoiceScanProperties.maxImageEdge]. */
    private fun shrink(bytes: ByteArray, mediaType: String): Pair<ByteArray, String> {
        val image = runCatching { ImageIO.read(bytes.inputStream()) }.getOrNull() ?: return bytes to mediaType // e.g. WebP: sent as is
        val edge = maxOf(image.width, image.height)
        if (edge <= props.maxImageEdge && bytes.size <= 3_500_000) return bytes to mediaType
        val scale = minOf(1.0, props.maxImageEdge.toDouble() / edge)
        val w = (image.width * scale).toInt().coerceAtLeast(1)
        val h = (image.height * scale).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(image, 0, 0, w, h, java.awt.Color.WHITE, null)
            dispose()
        }
        val buffer = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        ImageIO.createImageOutputStream(buffer).use { stream ->
            writer.output = stream
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.88f
            }
            writer.write(null, IIOImage(out, null, null), param)
            writer.dispose()
        }
        return buffer.toByteArray() to "image/jpeg"
    }

    companion object {
        val SYSTEM_PROMPT = """
            You read Romanian supplier documents for a restaurant's purchasing department: invoices (factură), delivery notes
            (aviz de însoțire a mărfii, including cash-and-carry ones) and receipts (bon fiscal). Your output is reviewed by a
            person before anything is saved, so report what is printed and leave out what is not.

            - Copy names, codes and addresses as printed. Do not translate, shorten or correct product names.
            - Numbers: digits exactly as printed, with "." as the decimal separator and no thousands separators
              ("28,240" becomes "28.240", "1.234,56" becomes "1234.56"). Never round, and never compute a value that is not printed.
            - Dates as YYYY-MM-DD, time as HH:MM (24 h).
            - docType: FACTURA for an invoice, AVIZ for a delivery note, BON for a fiscal receipt.
            - supplier is the company that sold the goods (usually with the logo); buyer is the customer company.
              supplierBranch is the store or branch block (name and address) when it is printed separately from the registered office.
              buyerPerson is the person named as buyer ("Cumpărător"). The CUI is the fiscal code (Cod fiscal / C.I.F.), keep a RO prefix if printed.
            - clientNo, cardNo, registerNo and cashierNo come from fields such as "Nr. client", "Nr. legitimație", "Număr casă", "Număr casier".
            - lines: every product line, in printed order, across all pages. quantity is the billed quantity ("Cant."), unit the unit
              as printed ("KG", "BU", "L"), unitPrice the net unit price, net / vat / gross the line values. code is the article code or EAN, lot the batch ("Lot").
              Lines for deposits (garanție SGR) or discounts are lines too.
            - vatBreakdown: the summary per VAT rate. totalNet / totalVat / totalGross: the document totals.
            - payments: each payment line, with method CASH (numerar), CARD, TRANSFER or OTHER; change given back is a negative amount.
            - discountTotal: the total discount when printed, as a negative number.
            - Use null for anything that is not on the document. If the image is not a supplier document, return no lines.
        """.trimIndent()

        const val USER_PROMPT = "Read this supplier document."

        private fun str() = mapOf("type" to listOf("string", "null"))

        private fun obj(vararg fields: Pair<String, Any>): Map<String, Any> =
            mapOf("type" to "object", "properties" to mapOf(*fields), "required" to fields.map { it.first }, "additionalProperties" to false)

        private fun arr(item: Map<String, Any>) = mapOf("type" to "array", "items" to item)

        private val party = obj("name" to str(), "cui" to str(), "regCom" to str(), "address" to str(), "iban" to str(), "phone" to str())

        /** Mirrors [ExtractedInvoice]; strict structured outputs need every field listed as required (nullable instead). */
        val SCHEMA: Map<String, Any> = obj(
            "docType" to mapOf("type" to listOf("string", "null"), "enum" to listOf("FACTURA", "AVIZ", "BON", null)),
            "series" to str(), "number" to str(), "issueDate" to str(), "issueTime" to str(), "dueDate" to str(), "currency" to str(),
            "supplier" to party, "supplierBranch" to str(), "buyer" to party, "buyerPerson" to str(),
            "clientNo" to str(), "cardNo" to str(), "registerNo" to str(), "cashierNo" to str(),
            "lines" to arr(
                obj(
                    "code" to str(), "description" to str(), "lot" to str(), "unit" to str(), "quantity" to str(), "unitPrice" to str(),
                    "vatPercent" to str(), "net" to str(), "vat" to str(), "gross" to str(),
                ),
            ),
            "vatBreakdown" to arr(obj("percent" to str(), "net" to str(), "vat" to str(), "gross" to str())),
            "totalNet" to str(), "totalVat" to str(), "totalGross" to str(), "discountTotal" to str(),
            "payments" to arr(obj("method" to str(), "amount" to str())),
        )

        fun schema(): JsonOutputFormat.Schema {
            val builder = JsonOutputFormat.Schema.builder()
            SCHEMA.forEach { (k, v) -> builder.putAdditionalProperty(k, JsonValue.from(v)) }
            return builder.build()
        }
    }
}
