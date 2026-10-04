package ro.amadya.kitchen.internal

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.KitchenApi
import ro.amadya.contract.model.KitchenTicket
import ro.amadya.contract.model.Station
import ro.amadya.settings.RestaurantSettingsApi
import java.util.Locale
import java.util.UUID

@RestController
@Transactional
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','KITCHEN')")
class KitchenController(
    private val kitchen: KitchenService,
    private val mapper: KitchenTicketMapper,
    private val settings: RestaurantSettingsApi,
) : KitchenApi {

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

    private fun KitchenTicketEntity.toDto(locale: Locale) = mapper.toDto(this, locale)

    override fun listKitchenStations(): ResponseEntity<List<Station>> =
        ResponseEntity.ok(settings.stations().filter { it.active }.map { Station(it.id, it.code, it.name.toDto(), it.parallelSlots, it.active) })
}
