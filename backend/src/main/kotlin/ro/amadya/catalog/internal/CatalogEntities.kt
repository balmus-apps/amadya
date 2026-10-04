package ro.amadya.catalog.internal

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ro.amadya.shared.BaseEntity
import ro.amadya.shared.Ids
import java.math.BigDecimal
import java.util.UUID

enum class ProductKind { RECIPE, RESALE, SERVICE }

@Entity
@Table(name = "category")
class CategoryEntity(
    @JdbcTypeCode(SqlTypes.JSON) var name: MutableMap<String, String>,
    @Column(name = "sort_order") var sortOrder: Int = 0,
    var active: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "product")
class ProductEntity(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id") var category: CategoryEntity,
    @JdbcTypeCode(SqlTypes.JSON) var name: MutableMap<String, String>,
    @JdbcTypeCode(SqlTypes.JSON) var description: MutableMap<String, String>? = null,
    @Column(name = "image_url") var imageUrl: String? = null,
    var price: BigDecimal,
    @Column(name = "vat_rate_id") var vatRateId: UUID,
    @Column(name = "station_id") var stationId: UUID? = null,
    @Enumerated(EnumType.STRING) var kind: ProductKind = ProductKind.RECIPE,
    @Column(name = "prep_time_sec") var prepTimeSec: Int = 300,
    var available: Boolean = true,
    var archived: Boolean = false,
    @Column(name = "sort_order") var sortOrder: Int = 0,
    @JdbcTypeCode(SqlTypes.ARRAY) var allergens: MutableList<String> = mutableListOf(),
    @ManyToMany
    @JoinTable(
        name = "product_modifier_group",
        joinColumns = [JoinColumn(name = "product_id")],
        inverseJoinColumns = [JoinColumn(name = "group_id")],
    )
    @OrderColumn(name = "sort_order")
    var modifierGroups: MutableList<ModifierGroupEntity> = mutableListOf(),
) : BaseEntity()

@Entity
@Table(name = "modifier_group")
class ModifierGroupEntity(
    @JdbcTypeCode(SqlTypes.JSON) var name: MutableMap<String, String>,
    @Column(name = "min_select") var minSelect: Int = 0,
    @Column(name = "max_select") var maxSelect: Int = 1,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "group_id", nullable = false)
    @OrderBy("sortOrder ASC")
    var options: MutableList<ModifierOptionEntity> = mutableListOf(),
) : BaseEntity()

@Entity
@Table(name = "modifier_option")
class ModifierOptionEntity(
    @Id val id: UUID = Ids.newId(),
    @JdbcTypeCode(SqlTypes.JSON) var name: MutableMap<String, String>,
    @Column(name = "price_delta") var priceDelta: BigDecimal = BigDecimal.ZERO,
    var available: Boolean = true,
    @Column(name = "sort_order") var sortOrder: Int = 0,
)

@Entity
@Table(name = "promotion")
class PromotionEntity(
    @JdbcTypeCode(SqlTypes.JSON) var title: MutableMap<String, String>,
    @JdbcTypeCode(SqlTypes.JSON) var subtitle: MutableMap<String, String>? = null,
    var badge: String? = null,
    @Column(name = "image_url") var imageUrl: String? = null,
    @Column(name = "product_id") var productId: UUID? = null,
    @Column(name = "starts_at") var startsAt: java.time.Instant? = null,
    @Column(name = "ends_at") var endsAt: java.time.Instant? = null,
    @Column(name = "sort_order") var sortOrder: Int = 0,
    var active: Boolean = true,
) : BaseEntity()
