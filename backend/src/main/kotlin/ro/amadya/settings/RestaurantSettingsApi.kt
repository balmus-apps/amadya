package ro.amadya.settings

import ro.amadya.shared.LocalizedText
import java.math.BigDecimal
import java.util.UUID

/** Read-only view of the restaurant configuration for other modules. */
interface RestaurantSettingsApi {
    fun name(): String
    fun currency(): String
    fun features(): FeatureFlags
    fun orderNumberPrefix(): String
    /** QR ordering: guest rounds and guest-started tables are confirmed automatically after this many minutes. */
    fun guestConfirmMinutes(): Int
    /** A customer's to-go order paid at pickup waits this many minutes for the cashier; 0 = no confirmation. */
    fun takeawayConfirmMinutes(): Int
    /** The activity log deletes staff actions older than this many days. */
    fun activityRetentionDays(): Int
    fun logoUrl(): String?
    fun vatRate(id: UUID): VatRateInfo?
    fun station(id: UUID): StationInfo?
    fun stations(): List<StationInfo>
}

data class FeatureFlags(
    val takeaway: Boolean,
    val tables: Boolean,
    val onlinePayments: Boolean,
    val kitchenDisplay: Boolean,
    val queueDisplay: Boolean,
    val invoiceOcr: Boolean,
    val qrOrdering: Boolean = false,
)

data class VatRateInfo(val id: UUID, val code: String, val percent: BigDecimal, val fiscalGroup: String, val active: Boolean)

data class StationInfo(val id: UUID, val code: String, val name: LocalizedText, val parallelSlots: Int, val active: Boolean)
