package ro.amadya.settings.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RestaurantSettingsRepository : JpaRepository<RestaurantSettingsEntity, Short>

interface VatRateRepository : JpaRepository<VatRateEntity, UUID> {
    fun existsByCodeIgnoreCase(code: String): Boolean
    fun findAllByOrderByPercentDesc(): List<VatRateEntity>
}

interface StationRepository : JpaRepository<StationEntity, UUID> {
    fun existsByCodeIgnoreCase(code: String): Boolean
    fun findAllByOrderByCodeAsc(): List<StationEntity>
}
