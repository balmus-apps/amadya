package ro.amadya.procurement.internal

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam

/**
 * ADR 0006 reader without AI: Tesseract OCR on this server, then [InvoiceTextParser]. A PDF with a text layer is read
 * directly (no OCR). One OCR runs at a time, so a scan never takes more than one core of the shared server.
 * Exists only when `amadya.invoice-scan.enabled=true`; reports itself unavailable when the program is missing.
 */
@Component
@ConditionalOnProperty(name = ["amadya.invoice-scan.enabled"], havingValue = "true")
class TesseractInvoiceExtractor(private val props: InvoiceScanProperties, private val json: JsonMapper) : InvoiceExtractor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val oneAtATime = Semaphore(1)

    private val version: String? by lazy {
        runCatching {
            val p = ProcessBuilder(props.tesseractPath, "--version").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) out.lineSequence().first().trim() else null
        }.getOrNull().also { v ->
            if (v == null) log.warn("Invoice scan is enabled but '{}' cannot be run; the Import button stays hidden", props.tesseractPath)
            else log.info("Invoice scan reads documents with {}", v)
        }
    }

    override fun available(): Boolean = version != null

    override fun extract(bytes: ByteArray, mediaType: String): Extraction {
        val (engine, text) = if (mediaType == "application/pdf") pdf(bytes) else "tesseract" to ocr(listOf(prepare(decode(bytes))))
        if (text.isBlank()) throw ExtractionFailed("no text found")
        val parsed = InvoiceTextParser.parse(text)
        val raw = json.writeValueAsString(mapOf("engine" to engine, "version" to version, "template" to parsed.template, "text" to text))
        return Extraction(parsed.invoice, raw)
    }

    /** Text layer when the PDF has one; otherwise its pages (at most 5) rendered and read by OCR. */
    private fun pdf(bytes: ByteArray): Pair<String, String> = try {
        Loader.loadPDF(bytes).use { doc ->
            val layer = PDFTextStripper().apply { sortByPosition = true }.getText(doc)
            if (layer.count { it.isLetter() } >= 40) {
                "pdf-text" to layer
            } else {
                val renderer = PDFRenderer(doc)
                val pages = (0 until minOf(doc.numberOfPages, 5)).map { prepare(renderer.renderImageWithDPI(it, 200f, ImageType.GRAY)) }
                "tesseract" to ocr(pages)
            }
        }
    } catch (e: ExtractionFailed) {
        throw e
    } catch (e: Exception) {
        throw ExtractionFailed("unreadable PDF", e)
    }

    /** Large photos are decoded subsampled, so a 12 MP picture never needs its full raster in the heap. */
    private fun decode(bytes: ByteArray): BufferedImage {
        ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: throw ExtractionFailed("unsupported image (use JPEG or PNG)")
            try {
                reader.input = input
                val edge = maxOf(reader.getWidth(0), reader.getHeight(0))
                val step = (edge / props.maxImageEdge).coerceAtLeast(1)
                val param: ImageReadParam = reader.defaultReadParam.apply { if (step > 1) setSourceSubsampling(step, step, 0, 0) }
                return reader.read(0, param)
            } finally {
                reader.dispose()
            }
        }
    }

    /**
     * Tesseract reads best at about 30 px per character: small phone photos are scaled up and large ones down to
     * [InvoiceScanProperties.maxImageEdge] × 1.5, turned gray and contrast-stretched (measured on the Selgros photo:
     * every number of every row correct, against garbled rows on the raw photo).
     */
    fun prepare(source: BufferedImage): BufferedImage {
        val target = props.maxImageEdge * 3 / 2
        val scale = target.toDouble() / maxOf(source.width, source.height)
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val gray = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
        gray.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(source, 0, 0, w, h, java.awt.Color.WHITE, null)
            dispose()
        }
        stretch(gray)
        return gray
    }

    /** Maps the 1st..99th percentile of gray levels to 0..255 (like ImageMagick's -normalize). */
    private fun stretch(image: BufferedImage) {
        val raster = image.raster
        val pixels = IntArray(image.width * image.height)
        raster.getSamples(0, 0, image.width, image.height, 0, pixels)
        val histogram = IntArray(256)
        pixels.forEach { histogram[it]++ }
        fun percentile(p: Double): Int {
            val goal = (pixels.size * p).toLong()
            var sum = 0L
            for (v in 0..255) {
                sum += histogram[v]
                if (sum >= goal) return v
            }
            return 255
        }
        val low = percentile(0.01)
        val high = percentile(0.99)
        if (high - low < 10) return
        for (i in pixels.indices) pixels[i] = ((pixels[i] - low) * 255 / (high - low)).coerceIn(0, 255)
        raster.setSamples(0, 0, image.width, image.height, 0, pixels)
    }

    /** Runs tesseract on each page, one document at a time; the text of all pages, in order. */
    private fun ocr(pages: List<BufferedImage>): String {
        if (version == null) throw ExtractionFailed("the OCR program is not installed")
        if (!oneAtATime.tryAcquire(30, TimeUnit.SECONDS)) throw ExtractionFailed("another document is being read, try again in a moment")
        val dir = Files.createTempDirectory("amadya-ocr")
        try {
            return pages.mapIndexed { i, page ->
                val image = dir.resolve("page$i.png")
                ImageIO.write(page, "png", image.toFile())
                run(image, dir.resolve("page$i"))
            }.joinToString("\n")
        } finally {
            oneAtATime.release()
            dir.toFile().deleteRecursively()
        }
    }

    private fun run(image: Path, outBase: Path): String {
        val started = System.nanoTime()
        val process = ProcessBuilder(props.tesseractPath, image.toString(), outBase.toString(), "-l", props.languages, "--psm", "6", "txt")
            .redirectErrorStream(true).redirectOutput(outBase.resolveSibling("${outBase.fileName}.log").toFile())
            .apply { environment()["OMP_THREAD_LIMIT"] = "1" }
            .start()
        if (!process.waitFor(props.timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            throw ExtractionFailed("reading took longer than ${props.timeout.seconds} s")
        }
        val text = outBase.resolveSibling("${outBase.fileName}.txt").toFile().takeIf { it.exists() }?.readText()
        if (process.exitValue() != 0 || text == null) throw ExtractionFailed("the OCR program failed (${process.exitValue()})")
        log.info("Invoice page read in {} ms", (System.nanoTime() - started) / 1_000_000)
        return text
    }
}
