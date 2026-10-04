package ro.amadya.kitchen.internal

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.KitchenApi
import ro.amadya.contract.model.KitchenTicket
import ro.amadya.contract.model.KitchenTicketLine
import ro.amadya.shared.localized
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import ro.amadya.contract.model.KitchenTicketStatus as KitchenTicketStatusDto
import ro.amadya.contract.model.OrderChannel as OrderChannelDto

@RestController
@Transactional
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','KITCHEN')")
class KitchenController(private val kitchen: KitchenService) : KitchenApi {

    override fun listKitchenTickets(stationId: UUID?): ResponseEntity<List<KitchenTicket>> {
        val locale = LocaleContextHolder.getLocale()
        return ResponseEntity.ok(kitchen.openTickets(stationId).map { it.toDto(locale) })
    }

    override fun startKitchenTicket(ticketId: UUID): ResponseEntity<KitchenTicket> =
        ResponseEntity.ok(kitchen.start(ticketId).toDto(LocaleContextHolder.getLocale()))

    override fun bumpKitchenTicket(ticketId: UUID): ResponseEntity<KitchenTicket> =
        ResponseEntity.ok(kitchen.bump(ticketId).toDto(LocaleContextHolder.getLocale()))

    override fun recallKitchenTicket(ticketId: UUID): ResponseEntity<KitchenTicket> =
        ResponseEntity.ok(kitchen.recall(ticketId).toDto(LocaleContextHolder.getLocale()))

    private fun KitchenTicketEntity.toDto(locale: Locale) = KitchenTicket(
        id = id,
        orderId = orderId,
        orderNumber = orderNumber,
        channel = OrderChannelDto.forValue(channel),
        stationId = stationId,
        status = KitchenTicketStatusDto.forValue(status.name),
        queuedAt = queuedAt.atOffset(ZoneOffset.UTC),
        estimatedReadyAt = estimatedReadyAt.atOffset(ZoneOffset.UTC),
        lines = lines.map { l -> KitchenTicketLine(l.productName.localized(locale), l.quantity, l.modifiers.map { it.localized(locale) }, l.notes) },
        startedAt = startedAt?.atOffset(ZoneOffset.UTC),
        readyAt = readyAt?.atOffset(ZoneOffset.UTC),
        notes = notes,
    )
}
