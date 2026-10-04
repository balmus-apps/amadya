package ro.amadya.settings.internal

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.contract.model.Features
import ro.amadya.contract.model.OpeningHours
import ro.amadya.contract.model.PublicSettings
import ro.amadya.contract.model.RestaurantSettings
import ro.amadya.contract.model.Station
import ro.amadya.contract.model.StationRequest
import ro.amadya.contract.model.VatRate
import ro.amadya.contract.model.VatRateRequest
import ro.amadya.settings.FeatureFlags
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.settings.StationInfo
import ro.amadya.settings.VatRateInfo
import ro.amadya.shared.ConflictException
import ro.amadya.shared.LocalizedText
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.UnprocessableException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import ro.amadya.contract.model.Locale as LocaleDto

@Service
@Transactional(readOnly = true)
class SettingsService(
    private val settings: RestaurantSettingsRepository,
    private val vatRates: VatRateRepository,
    private val stations: StationRepository,
) : RestaurantSettingsApi {

    private fun row(): RestaurantSettingsEntity = settings.findByIdOrNull(1) ?: error("restaurant_settings row missing")

    // ---------------------------------------------------------------- RestaurantSettingsApi
    override fun name(): String = row().name

    override fun currency(): String = row().currency

    override fun features(): FeatureFlags = row().features.let {
        FeatureFlags(
            takeaway = it["takeaway"] ?: true,
            tables = it["tables"] ?: true,
            onlinePayments = it["onlinePayments"] ?: false,
            kitchenDisplay = it["kitchenDisplay"] ?: true,
            queueDisplay = it["queueDisplay"] ?: true,
            invoiceOcr = it["invoiceOcr"] ?: false,
        )
    }

    override fun orderNumberPrefix(): String = row().orderNumberPrefix

    override fun vatRate(id: UUID): VatRateInfo? =
        vatRates.findByIdOrNull(id)?.let { VatRateInfo(it.id, it.code, it.percent, it.fiscalGroup, it.active) }

    override fun station(id: UUID): StationInfo? =
        stations.findByIdOrNull(id)?.let { StationInfo(it.id, it.code, LocalizedText.fromMap(it.name)!!, it.parallelSlots, it.active) }

    // ---------------------------------------------------------------- restaurant settings
    fun publicSettings(): PublicSettings = row().let {
        PublicSettings(
            name = it.name,
            defaultLocale = LocaleDto.forValue(it.defaultLocale),
            locales = it.locales.map(LocaleDto::forValue),
            currency = it.currency,
            theme = it.theme,
            features = features().toDto(),
            openingHours = it.openingHours.map(::toOpeningHours),
            logoUrl = it.logoUrl,
            phone = it.phone,
            address = it.address,
        )
    }

    fun restaurantSettings(): RestaurantSettings = row().toDto()

    @Transactional
    fun updateRestaurantSettings(req: RestaurantSettings): RestaurantSettings {
        if (req.defaultLocale !in req.locales) throw UnprocessableException("settings.locale_not_supported")
        val row = row()
        row.name = req.name.trim()
        row.legalName = req.legalName
        row.cui = req.cui
        row.regCom = req.regCom
        row.address = req.address
        row.phone = req.phone
        row.email = req.email
        row.logoUrl = req.logoUrl
        row.defaultLocale = req.defaultLocale.value
        row.locales = req.locales.map { it.value }.distinct().toMutableList()
        row.currency = req.currency
        row.theme = req.theme.toMutableMap()
        row.features = req.features.let {
            mutableMapOf(
                "takeaway" to it.takeaway, "tables" to it.tables, "onlinePayments" to it.onlinePayments,
                "kitchenDisplay" to it.kitchenDisplay, "queueDisplay" to it.queueDisplay, "invoiceOcr" to it.invoiceOcr,
            )
        }
        row.openingHours = req.openingHours.map { mapOf("dayOfWeek" to it.dayOfWeek, "opens" to it.opens, "closes" to it.closes) }.toMutableList()
        req.orderNumberPrefix?.takeIf { it.isNotBlank() }?.let { row.orderNumberPrefix = it.trim().uppercase() }
        return row.toDto()
    }

    // ---------------------------------------------------------------- VAT rates
    fun listVatRates(): List<VatRate> = vatRates.findAllByOrderByPercentDesc().map { it.toDto() }

    @Transactional
    fun createVatRate(req: VatRateRequest): VatRate {
        if (vatRates.existsByCodeIgnoreCase(req.code)) throw ConflictException("vat.code_taken", req.code)
        return vatRates.save(
            VatRateEntity(req.code.trim().uppercase(), LocalizedText.of(req.name).toMap(), percent(req.percent), req.fiscalGroup, req.active ?: true),
        ).toDto()
    }

    @Transactional
    fun updateVatRate(id: UUID, req: VatRateRequest): VatRate {
        val rate = vatRates.findByIdOrNull(id) ?: throw NotFoundException("VAT rate")
        if (!rate.code.equals(req.code, ignoreCase = true) && vatRates.existsByCodeIgnoreCase(req.code)) {
            throw ConflictException("vat.code_taken", req.code)
        }
        rate.code = req.code.trim().uppercase()
        rate.name = LocalizedText.of(req.name).toMap()
        rate.percent = percent(req.percent)
        rate.fiscalGroup = req.fiscalGroup
        rate.active = req.active ?: rate.active
        return rate.toDto()
    }

    // ---------------------------------------------------------------- stations
    fun listStations(): List<Station> = stations.findAllByOrderByCodeAsc().map { it.toDto() }

    @Transactional
    fun createStation(req: StationRequest): Station {
        if (stations.existsByCodeIgnoreCase(req.code)) throw ConflictException("station.code_taken", req.code)
        return stations.save(StationEntity(req.code, LocalizedText.of(req.name).toMap(), req.parallelSlots ?: 2, req.active ?: true)).toDto()
    }

    @Transactional
    fun updateStation(id: UUID, req: StationRequest): Station {
        val station = stations.findByIdOrNull(id) ?: throw NotFoundException("Station")
        if (!station.code.equals(req.code, ignoreCase = true) && stations.existsByCodeIgnoreCase(req.code)) {
            throw ConflictException("station.code_taken", req.code)
        }
        station.code = req.code
        station.name = LocalizedText.of(req.name).toMap()
        station.parallelSlots = req.parallelSlots ?: station.parallelSlots
        station.active = req.active ?: station.active
        return station.toDto()
    }

    // ---------------------------------------------------------------- mapping
    private fun percent(value: String) = BigDecimal(value).setScale(2, RoundingMode.HALF_UP)

    private fun FeatureFlags.toDto() = Features(takeaway, tables, onlinePayments, kitchenDisplay, queueDisplay, invoiceOcr)

    private fun toOpeningHours(map: Map<String, Any>) =
        OpeningHours(dayOfWeek = (map["dayOfWeek"] as Number).toInt(), opens = map["opens"].toString(), closes = map["closes"].toString())

    private fun RestaurantSettingsEntity.toDto() = RestaurantSettings(
        name = name,
        defaultLocale = LocaleDto.forValue(defaultLocale),
        locales = locales.map(LocaleDto::forValue),
        currency = currency,
        theme = theme,
        features = features().toDto(),
        openingHours = openingHours.map(::toOpeningHours),
        legalName = legalName,
        cui = cui,
        regCom = regCom,
        address = address,
        phone = phone,
        email = email,
        logoUrl = logoUrl,
        orderNumberPrefix = orderNumberPrefix,
    )

    private fun VatRateEntity.toDto() =
        VatRate(id, code, LocalizedText.fromMap(name)!!.toDto(), percent.setScale(2).toPlainString(), fiscalGroup, active)

    private fun StationEntity.toDto() = Station(id, code, LocalizedText.fromMap(name)!!.toDto(), parallelSlots, active)
}
