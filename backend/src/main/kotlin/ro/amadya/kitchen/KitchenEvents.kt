package ro.amadya.kitchen

import ro.amadya.shared.LocalizedText
import java.time.Instant
import java.util.UUID

/** A ticket was queued at a station; consumed by printing (kitchen printer) and, later, the KDS live feed. */
data class KitchenTicketCreated(
    val ticketId: UUID,
    val orderId: UUID,
    val orderNumber: String,
    val channel: String,
    val stationId: UUID,
    val queuedAt: Instant,
    val estimatedReadyAt: Instant,
    val notes: String?,
    val lines: List<TicketLine>,
)

data class TicketLine(val productName: LocalizedText, val quantity: Int, val modifiers: List<LocalizedText>, val notes: String?)
