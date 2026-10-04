package ro.amadya.settings.internal

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.SettingsApi
import ro.amadya.contract.model.PublicSettings
import ro.amadya.contract.model.RestaurantSettings
import ro.amadya.contract.model.Station
import ro.amadya.contract.model.StationRequest
import ro.amadya.contract.model.VatRate
import ro.amadya.contract.model.VatRateRequest
import java.util.UUID

@RestController
class SettingsController(private val service: SettingsService) : SettingsApi {

    override fun getPublicSettings(): ResponseEntity<PublicSettings> = ResponseEntity.ok(service.publicSettings())

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    override fun getRestaurantSettings(): ResponseEntity<RestaurantSettings> = ResponseEntity.ok(service.restaurantSettings())

    @PreAuthorize("hasRole('ADMIN')")
    override fun updateRestaurantSettings(restaurantSettings: RestaurantSettings): ResponseEntity<RestaurantSettings> =
        ResponseEntity.ok(service.updateRestaurantSettings(restaurantSettings))

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    override fun listVatRates(): ResponseEntity<List<VatRate>> = ResponseEntity.ok(service.listVatRates())

    @PreAuthorize("hasRole('ADMIN')")
    override fun createVatRate(vatRateRequest: VatRateRequest): ResponseEntity<VatRate> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.createVatRate(vatRateRequest))

    @PreAuthorize("hasRole('ADMIN')")
    override fun updateVatRate(id: UUID, vatRateRequest: VatRateRequest): ResponseEntity<VatRate> =
        ResponseEntity.ok(service.updateVatRate(id, vatRateRequest))

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    override fun listStations(): ResponseEntity<List<Station>> = ResponseEntity.ok(service.listStations())

    @PreAuthorize("hasRole('ADMIN')")
    override fun createStation(stationRequest: StationRequest): ResponseEntity<Station> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.createStation(stationRequest))

    @PreAuthorize("hasRole('ADMIN')")
    override fun updateStation(id: UUID, stationRequest: StationRequest): ResponseEntity<Station> =
        ResponseEntity.ok(service.updateStation(id, stationRequest))
}
