package ro.amadya.settings.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ro.amadya.shared.BaseEntity
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "restaurant_settings")
class RestaurantSettingsEntity(
    @Id
    val id: Short = 1,
    var name: String,
    @Column(name = "legal_name") var legalName: String? = null,
    var cui: String? = null,
    @Column(name = "reg_com") var regCom: String? = null,
    var address: String? = null,
    var phone: String? = null,
    var email: String? = null,
    @Column(name = "logo_url") var logoUrl: String? = null,
    @Column(name = "default_locale") var defaultLocale: String,
    @JdbcTypeCode(SqlTypes.ARRAY) var locales: MutableList<String>,
    var currency: String,
    @JdbcTypeCode(SqlTypes.JSON) var theme: MutableMap<String, String>,
    @JdbcTypeCode(SqlTypes.JSON) var features: MutableMap<String, Boolean>,
    @Column(name = "opening_hours") @JdbcTypeCode(SqlTypes.JSON) var openingHours: MutableList<Map<String, Any>>,
    @Column(name = "order_number_prefix") var orderNumberPrefix: String,
) {
    @Column(name = "created_at", updatable = false) var createdAt: Instant = Instant.now()
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
    @Version var version: Long = 0

    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(name = "vat_rate")
class VatRateEntity(
    var code: String,
    @JdbcTypeCode(SqlTypes.JSON) var name: MutableMap<String, String>,
    var percent: BigDecimal,
    @Column(name = "fiscal_group") var fiscalGroup: String,
    var active: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "station")
class StationEntity(
    var code: String,
    @JdbcTypeCode(SqlTypes.JSON) var name: MutableMap<String, String>,
    @Column(name = "parallel_slots") var parallelSlots: Int = 2,
    var active: Boolean = true,
) : BaseEntity()
