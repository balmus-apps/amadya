package ro.amadya.procurement.internal

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Turns the text of a supplier document (from OCR or a PDF text layer) into [ExtractedInvoice]. A template per supplier
 * layout knows where things are; the generic template finds what any Romanian invoice has (CUIs, date, totals, rows
 * that add up). Values are copied as read; the review page and the totals cross-check catch OCR mistakes.
 */
object InvoiceTextParser {

    interface Template {
        val name: String
        fun matches(text: Text): Boolean
        fun parse(text: Text): ExtractedInvoice
    }

    /** The document's lines, with a folded copy (upper case, no diacritics) for finding labels. */
    class Text(raw: String) {
        val lines: List<String> = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val folded: List<String> = lines.map(ProductNameRules::fold)
        val all: String = folded.joinToString("\n")

        fun indexOf(label: String, from: Int = 0): Int = (from until folded.size).firstOrNull { folded[it].contains(label) } ?: -1
    }

    private val templates: List<Template> = listOf(SelgrosTemplate, GenericTemplate)

    data class Result(val template: String, val invoice: ExtractedInvoice)

    fun parse(raw: String): Result {
        val text = Text(raw)
        val template = templates.first { it.matches(text) }
        return Result(template.name, template.parse(text))
    }

    // ---------------------------------------------------------------- shared helpers
    /** Amounts with two decimals ("137,17", "-12,83", "1.234,56"); a bare "-4," does not count. */
    internal val MONEY = Regex("-?\\d{1,3}(?:[.\\s]\\d{3})+[.,]\\d{2}(?!\\d)|-?\\d+[.,]\\d{2}(?!\\d)")
    private val DATE = Regex("\\b(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})\\b")
    private val TIME = Regex("\\b([01]?\\d|2[0-3])[:.;]([0-5]\\d)\\b")
    private val CUI = Regex("(?:C\\.?\\s?U\\.?\\s?I|C\\.?\\s?[I1L]\\.?\\s?F|COD\\s+FISCAL|COD\\s+DE\\s+INREGISTRARE)\\.?\\s*[:.]?\\s*(RO\\s?)?(\\d{2,10})\\b")
    private val REG_COM = Regex("\\b([JFC]\\s?\\d{2}\\s?/?\\s?\\d{1,8}\\s?/?\\s?\\d{0,8})\\b")
    private val IBAN = Regex("\\bRO\\d{2}\\s?[A-Z0-9]{4}(?:\\s?[A-Z0-9]{4}){4}\\b")

    internal fun date(line: String): String? = DATE.find(line)?.let { m ->
        val (d, mo, y) = m.destructured
        "%s-%02d-%02d".format(y, mo.toInt(), d.toInt()).takeIf { mo.toInt() in 1..12 && d.toInt() in 1..31 }
    }

    internal fun time(line: String): String? = TIME.find(line)?.let { "%02d:%s".format(it.groupValues[1].toInt(), it.groupValues[2]) }

    internal fun money(line: String): List<String> = MONEY.findAll(line).map { it.value.replace(" ", "") }.toList()

    internal fun docType(text: Text): String = when {
        text.all.contains("AVIZ") -> "AVIZ"
        text.all.contains("BON FISCAL") -> "BON"
        else -> "FACTURA"
    }

    /** OCR reads the letter O for zero inside account numbers; an IBAN's last 16 characters are mostly digits. */
    internal fun iban(text: String): String? = IBAN.find(text.uppercase())?.value?.replace(" ", "")

    internal fun cuis(text: Text): List<String> = CUI.findAll(text.all).map { it.groupValues[2] }.filter { InvoiceScanService.validCui(it) }.distinct().toList()

    internal fun regCom(text: Text): String? = REG_COM.find(text.all.substringAfter("REG", ""))?.value?.replace(" ", "")

    /**
     * A misread decimal separator ("9690" for "9,690") makes quantity × price far from the printed net value; when
     * dividing the price by 10, 100 or 1000 makes it add up, that is the price that was printed.
     */
    internal fun repairPrice(quantity: String?, price: String?, net: String?): String? {
        val q = num(quantity) ?: return price
        val p = num(price) ?: return price
        val n = num(net) ?: return price
        val tolerance = BigDecimal("0.02")
        if ((q * p).setScale(2, RoundingMode.HALF_UP).subtract(n).abs() <= tolerance) return price
        for (k in 1..3) {
            val scaled = p.movePointLeft(k)
            if ((q * scaled).setScale(2, RoundingMode.HALF_UP).subtract(n).abs() <= tolerance) return scaled.toPlainString()
        }
        return price
    }

    // ---------------------------------------------------------------- Selgros
    /** Selgros cash & carry "Aviz de însoțire a mărfii" / invoice (the layout in the change proposal). */
    object SelgrosTemplate : Template {
        override val name = "selgros"

        override fun matches(text: Text) = text.all.contains("SELGROS") || text.all.contains("11805367")

        /** pos [A] EAN description [S] UM price UV qty net VAT% VAT gross */
        private val ROW = Regex(
            "^\\S{0,2}\\s*(?:A\\s+)?(\\d{8,14})\\s+(.*?)\\s+(?:[Ss]\\s+)?([A-Za-z]{1,4})\\s+([\\d.,]+)\\s+(\\d{1,3})\\s+([\\d.,]+)\\s+([\\d.,]+)\\s+(\\d{1,2}[.,]\\d)\\s*%\\s+([\\d.,]+)\\s+([\\d.,]+)\\s*$",
        )

        override fun parse(text: Text): ExtractedInvoice {
            val l = text.lines
            val f = text.folded

            // Nr. document / Data document: the values are on the next line.
            var number: String? = null
            var issueDate: String? = null
            var issueTime: String? = null
            val docLabel = text.indexOf("NR. DOCUMENT")
            if (docLabel >= 0) {
                for (i in docLabel..minOf(docLabel + 3, l.lastIndex)) {
                    val n = Regex("\\b\\d{12,20}\\b").find(l[i]) ?: continue
                    number = n.value
                    issueDate = date(l[i].substring(n.range.last + 1))
                    issueTime = DATE.find(l[i])?.let { d -> time(l[i].substring(d.range.last + 1)) }
                    break
                }
            }

            // Nr. client, Nr. legitimație, Număr casă on one line; Număr casier starts the next one.
            var clientNo: String? = null
            var cardNo: String? = null
            var registerNo: String? = null
            var cashierNo: String? = null
            var afterIds = -1
            val clientLabel = text.indexOf("NR. CLIENT")
            if (clientLabel >= 0 && clientLabel + 1 <= l.lastIndex) {
                val ids = Regex("\\b\\d{2,12}\\b").findAll(l[clientLabel + 1]).map { it.value }.toList()
                clientNo = ids.getOrNull(0)
                cardNo = ids.getOrNull(1)
                registerNo = ids.getOrNull(2)
                val next = l.getOrNull(clientLabel + 2)
                cashierNo = next?.let { Regex("^\\d{6,10}\\b").find(it)?.value }
                afterIds = clientLabel + if (cashierNo != null) 3 else 2
            }

            // Buyer block (left column): name first, "Cod fiscal", "Cumpăr.". The right column is the Selgros store.
            val buyerName = l.getOrNull(afterIds)?.let { leftColumn(it) }?.takeIf { it.isNotBlank() && !it.contains(Regex("\\d{5,}")) }
            val buyerCui = Regex("COD FISCAL\\s*[:.]?\\s*(RO\\s?)?(\\d{2,10})").find(text.all)?.groupValues?.get(2)
            val buyerPerson = f.indexOfFirst { it.startsWith("CUMPAR") }.takeIf { it >= 0 }?.let { l[it].substringAfter(":", "").trim().ifEmpty { null } }
            val branch = Regex("Selgros\\s+(?!Cash|CASH)(\\p{L}[\\p{L}\\-]+)").find(l.joinToString("\n"))?.value

            // Product rows; "Lot:" and wrapped description lines belong to the row above.
            val rows = mutableListOf<ExtractedLine>()
            val end = text.indexOf("NR. UNIT").takeIf { it >= 0 } ?: text.indexOf("COTA TVA").takeIf { it >= 0 } ?: l.size
            var i = 0
            while (i < end) {
                val m = ROW.find(l[i])
                if (m == null) {
                    i++
                    continue
                }
                val g = m.groupValues
                var description = g[2].trim()
                var lot: String? = null
                var j = i + 1
                while (j < end && ROW.find(l[j]) == null) {
                    when {
                        f[j].startsWith("LOT") -> lot = Regex("\\d{4,}").find(l[j])?.value
                        l[j].split(Regex("\\s+")).size <= 4 && !l[j].contains(Regex("\\d+[.,]\\d{2}")) -> description = "$description ${l[j]}".trim()
                    }
                    j++
                }
                val net = g[7]
                val qty = g[6]
                rows += ExtractedLine(
                    code = g[1], description = description, lot = lot, unit = g[3].uppercase(), quantity = qty,
                    unitPrice = repairPrice(qty, g[4], net), vatPercent = g[8], net = net, vat = g[9], gross = g[10],
                )
                i = j
            }

            // Cotă TVA table, Total marfă, payments, discount.
            val vatStart = text.indexOf("COTA TVA")
            val vat = if (vatStart < 0) emptyList() else (vatStart + 1 until minOf(vatStart + 6, l.size)).mapNotNull { k ->
                Regex("^(\\d{1,2}[.,]\\d)\\s*%").find(l[k])?.let { rate ->
                    val amounts = money(l[k].substring(rate.range.last + 1))
                    if (amounts.size >= 3) ExtractedVat(rate.groupValues[1], amounts[0], amounts[1], amounts[2]) else null
                }
            }
            val totals = text.indexOf("TOTAL MARFA").takeIf { it >= 0 }?.let { money(l[it]) }.orEmpty()
            val totalToPay = text.indexOf("TOTAL DE PLATA").takeIf { it >= 0 }?.let { money(l[it]).lastOrNull() }
            val payments = paymentsBetween(text, text.indexOf("TOTAL DE PLATA"), text.indexOf("ECONOMISIT"))
            val discount = text.indexOf("REDUCEREA TOTALA").takeIf { it >= 0 }?.let { money(l[it]).lastOrNull() }
                ?.let { if (it.startsWith("-")) it else "-$it" }

            return ExtractedInvoice(
                docType = docType(text), number = number, issueDate = issueDate, issueTime = issueTime, currency = "RON",
                supplier = ExtractedParty(
                    name = "Selgros Cash & Carry S.R.L.", cui = cuis(text).firstOrNull { it == "11805367" } ?: "11805367",
                    regCom = regCom(text),
                ),
                supplierBranch = branch,
                buyer = ExtractedParty(name = buyerName, cui = buyerCui),
                buyerPerson = buyerPerson,
                clientNo = clientNo, cardNo = cardNo, registerNo = registerNo, cashierNo = cashierNo,
                lines = rows, vatBreakdown = vat,
                totalNet = totals.getOrNull(0), totalVat = totals.getOrNull(1), totalGross = totals.getOrNull(2) ?: totalToPay,
                discountTotal = discount, payments = payments,
            )
        }

        /** Two-column header: the right column starts where "Selgros" (or a long run of spaces) does. */
        private fun leftColumn(line: String): String = line.split(Regex("\\s{3,}|(?=Selgros)|(?=SELGROS)")).first().trim()
    }

    /** "Numerar : 150,00 RON" lines; OCR sometimes leaves the amount on its own line, which still counts. */
    private fun paymentsBetween(text: Text, from: Int, to: Int): List<ExtractedPayment> {
        if (from < 0) return emptyList()
        val until = if (to > from) to else minOf(from + 6, text.lines.size)
        return (from + 1 until until).flatMap { k ->
            val line = text.lines[k]
            val method = when {
                text.folded[k].contains("CARD") -> "CARD"
                text.folded[k].contains("ORDIN") || text.folded[k].contains("TRANSFER") -> "TRANSFER"
                else -> "CASH"
            }
            money(line).filter { line.uppercase().contains("RON") || text.folded[k].contains("NUMERAR") }.map { ExtractedPayment(method, it) }
        }
    }

    // ---------------------------------------------------------------- generic
    /** Any layout: parties by CUI, first date, number after "Nr."/"Factura", the largest "Total", rows that add up. */
    object GenericTemplate : Template {
        override val name = "generic"

        override fun matches(text: Text) = true

        private val UNITS = setOf("KG", "G", "GR", "L", "LT", "ML", "BUC", "BU", "BC", "PCS", "BAX", "SET", "PAC", "CUT", "SAC", "M")
        private val NUMBER = Regex("(?:FACTURA(?:\\s+FISCALA)?|AVIZ|NR\\.?\\s*(?:FACTURA|DOCUMENT)|SERIA\\s+\\S+)\\s*(?:NR\\.?|NUMAR)?\\s*[:.]?\\s*([A-Z]{0,6}[\\s-]?\\d{3,})")

        override fun parse(text: Text): ExtractedInvoice {
            val parties = partiesByRole(text)
            val number = NUMBER.find(text.all)?.groupValues?.get(1)?.replace(" ", "")
            val issueDate = text.lines.firstNotNullOfOrNull { date(it) }
            val gross = text.folded.indices.filter { text.folded[it].contains("TOTAL") }
                .flatMap { money(text.lines[it]) }.mapNotNull { v -> num(v)?.let { v to it } }.maxByOrNull { it.second }?.first
            return ExtractedInvoice(
                docType = docType(text), number = number, issueDate = issueDate, currency = "RON",
                supplier = ExtractedParty(name = parties.first.first, cui = parties.first.second, regCom = regCom(text), iban = iban(text.lines.joinToString(" "))),
                buyer = ExtractedParty(name = parties.second.first, cui = parties.second.second),
                lines = text.lines.mapNotNull(::row), totalGross = gross,
            )
        }

        /** Supplier near "Furnizor"/"Vânzător", buyer near "Cumpărător"/"Client"; otherwise the first CUI is the supplier. */
        private fun partiesByRole(text: Text): Pair<Pair<String?, String?>, Pair<String?, String?>> {
            val found = cuis(text)
            fun near(vararg labels: String): Pair<String?, String?>? {
                val at = text.folded.indexOfFirst { line -> labels.any { line.contains(it) } }
                if (at < 0) return null
                val window = (at until minOf(at + 8, text.lines.size))
                val cui = window.firstNotNullOfOrNull { k -> found.firstOrNull { text.folded[k].contains(it) } }
                val name = window.map { text.lines[it] }.firstOrNull { Regex("\\b(S\\.?R\\.?L|S\\.?A|I\\.?I|P\\.?F\\.?A)\\b\\.?", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                    ?.substringAfter(":")?.trim()
                return name to cui
            }
            val supplier = near("FURNIZOR", "VANZATOR", "EMITENT") ?: (null to found.getOrNull(0))
            val buyer = near("CUMPARATOR", "CLIENT", "BENEFICIAR") ?: (null to found.firstOrNull { it != supplier.second })
            return supplier to buyer
        }

        private val NUMERIC = Regex("^-?\\d[\\d.,]*%?$")

        /** A line ending in numbers where some quantity × price = value; the text before them is the description. */
        private fun row(line: String): ExtractedLine? {
            val tokens = line.split(Regex("\\s+"))
            var start = tokens.size
            while (start > 0 && NUMERIC.matches(tokens[start - 1])) start--
            val numbers = tokens.drop(start)
            if (numbers.size < 3 || start == 0) return null
            val values = numbers.map { it to num(it.removeSuffix("%")) }
            for (a in values.indices) for (b in a + 1 until values.size) for (c in b + 1 until values.size) {
                if (listOf(a, b, c).any { values[it].first.endsWith("%") }) continue
                val q = values[a].second ?: continue
                val p = values[b].second ?: continue
                val n = values[c].second ?: continue
                if (q.signum() <= 0 || n.signum() <= 0) continue
                if ((q * p).setScale(2, RoundingMode.HALF_UP).subtract(n).abs() <= BigDecimal("0.02")) {
                    val head = tokens.take(start).let { h -> if (h.size > 1 && h.first().matches(Regex("\\d{1,3}\\.?"))) h.drop(1) else h }
                    val unit = head.lastOrNull()?.uppercase()?.trimEnd('.')?.takeIf { it in UNITS }
                    val description = (if (unit != null) head.dropLast(1) else head).joinToString(" ").trim()
                    if (description.count { it.isLetter() } < 2) return null
                    val vat = numbers.firstOrNull { it.endsWith("%") }?.removeSuffix("%")
                    return ExtractedLine(description = description, unit = unit, quantity = values[a].first, unitPrice = values[b].first, vatPercent = vat, net = values[c].first)
                }
            }
            return null
        }
    }
}
