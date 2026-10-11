package ro.amadya.printing.internal

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.AmadyaProperties
import ro.amadya.tables.PreBillRequested
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/**
 * Pre-bill (nota de plată) for a table: what was ordered and what is left to pay. Not a fiscal document; the fiscal
 * receipt comes with the fiscal printer (Phase 6). Log mode only, like kitchen tickets (ADR 0003).
 */
@Component
class PreBillPrinting(
    private val settings: RestaurantSettingsApi,
    private val props: AmadyaProperties,
    @param:Value("\${amadya.printing.mode:log}") private val mode: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val time = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    @ApplicationModuleListener
    fun on(bill: PreBillRequested) {
        if (mode != "log") return
        log.info("[print:pre-bill] table {}\n{}", bill.tableLabel, render(bill))
    }

    fun render(bill: PreBillRequested, width: Int = 32): String {
        fun row(left: String, right: String) = left.take(width - right.length - 1).padEnd(width - right.length) + right
        fun money(amount: BigDecimal) = "%.2f".format(amount)
        return buildString {
            appendLine("=".repeat(width))
            appendLine(center(settings.name(), width))
            appendLine(center("NOTA DE PLATA", width))
            appendLine(center("Masa ${bill.tableLabel}", width))
            appendLine(time.format(bill.at.atZone(props.timezone)) + (bill.waiterName?.let { " · $it" } ?: ""))
            appendLine("-".repeat(width))
            bill.lines.forEach { appendLine(row("${it.quantity} x ${it.name}", money(it.total))) }
            appendLine("-".repeat(width))
            appendLine(row("TOTAL ${bill.currency}", money(bill.total)))
            if (bill.paid.signum() > 0) {
                appendLine(row("Achitat", money(bill.paid)))
                appendLine(row("Rest de plata", money(bill.remaining)))
            }
            appendLine(center("Nu este bon fiscal", width))
            append("=".repeat(width))
        }
    }

    private fun center(s: String, width: Int) = s.padStart((width + s.length) / 2).padEnd(width)
}
