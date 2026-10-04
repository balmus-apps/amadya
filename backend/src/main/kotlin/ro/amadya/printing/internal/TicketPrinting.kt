package ro.amadya.printing.internal

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import ro.amadya.kitchen.KitchenTicketCreated
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.AmadyaProperties
import java.nio.file.Files
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Kitchen ticket printing. Until the printer hardware is chosen (ADR 0003) only `log` mode exists:
 * the rendered ticket is written to the log and, when PRINTING_OUT_DIR is set, to a text file per ticket.
 * Phase 6 replaces this with print jobs sent to the on-site Print Bridge.
 */
@Component
class TicketPrinting(
    private val settings: RestaurantSettingsApi,
    private val props: AmadyaProperties,
    @param:Value("\${amadya.printing.mode:log}") private val mode: String,
    @param:Value("\${amadya.printing.out-dir:}") private val outDir: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    @ApplicationModuleListener
    fun on(ticket: KitchenTicketCreated) {
        if (mode != "log") return
        val text = render(ticket)
        log.info("[print:{}] kitchen ticket\n{}", ticket.stationId, text)
        if (outDir.isNotBlank()) {
            val dir = Files.createDirectories(Path.of(outDir))
            Files.writeString(dir.resolve("ticket-${ticket.orderNumber}-${ticket.ticketId}.txt"), text)
        }
    }

    fun render(ticket: KitchenTicketCreated, width: Int = 32): String {
        val ro = Locale.of("ro")
        val station = settings.station(ticket.stationId)?.name?.resolve(ro) ?: "?"
        val zone = props.timezone
        return buildString {
            appendLine("=".repeat(width))
            appendLine(center("#${ticket.orderNumber}", width))
            appendLine(center("${station.uppercase()} · ${ticket.channel}", width))
            appendLine("Primit ${time.format(ticket.queuedAt.atZone(zone))}  Gata ~${time.format(ticket.estimatedReadyAt.atZone(zone))}")
            appendLine("-".repeat(width))
            ticket.lines.forEach { line ->
                appendLine("${line.quantity} x ${line.productName.resolve(ro)}")
                line.modifiers.forEach { appendLine("    + ${it.resolve(ro)}") }
                line.notes?.let { appendLine("    ! $it") }
            }
            ticket.notes?.let { appendLine("-".repeat(width)); appendLine("NOTA: $it") }
            append("=".repeat(width))
        }
    }

    private fun center(s: String, width: Int) = s.padStart((width + s.length) / 2).padEnd(width)
}
