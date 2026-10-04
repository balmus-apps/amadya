package ro.amadya.settings

import ro.amadya.shared.LocalizedText
import java.math.BigDecimal
import java.util.UUID

/** Read-only view of the restaurant configuration for other modules. */
interface RestaurantSettingsApi {
    fun currency(): String
    fun features(): FeatureFlags
    fun orderNumberPrefix(): String
    fun vatRate(id: UUID): VatRateInfo?
    fun station(id: UUID): StationInfo?
}

data class FeatureFlags(
    val takeaway: Boolean,
    val tables: Boolean,
    val onlinePayments: Boolean,
    val kitchenDisplay: Boolean,
    val queueDisplay: Boolean,
    val invoiceOcr: Boolean,
)

data class VatRateInfo(val id: UUID, val code: String, val percent: BigDecimal, val fiscalGroup: String, val active: Boolean)

data class StationInfo(val id: UUID, val code: String, val name: LocalizedText, val parallelSlots: Int, val active: Boolean)
