package ro.amadya.catalog.internal

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.catalog.LineSelection
import ro.amadya.catalog.PricedLine
import ro.amadya.catalog.PricedModifier
import ro.amadya.catalog.ProductCatalog
import ro.amadya.contract.model.AdminModifierGroup
import ro.amadya.contract.model.AdminModifierOption
import ro.amadya.contract.model.Category
import ro.amadya.contract.model.CategoryRequest
import ro.amadya.contract.model.Menu
import ro.amadya.contract.model.MenuCategory
import ro.amadya.contract.model.MenuModifierGroup
import ro.amadya.contract.model.MenuModifierOption
import ro.amadya.contract.model.MenuProduct
import ro.amadya.contract.model.ModifierGroupRequest
import ro.amadya.contract.model.Product
import ro.amadya.contract.model.ProductRequest
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.ConflictException
import ro.amadya.shared.LocalizedText
import ro.amadya.shared.Money
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.UnprocessableException
import ro.amadya.shared.localized
import ro.amadya.shared.toMoney
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID
import ro.amadya.contract.model.ProductKind as ProductKindDto

@Service
@Transactional(readOnly = true)
class CatalogService(
    private val categories: CategoryRepository,
    private val products: ProductRepository,
    private val groups: ModifierGroupRepository,
    private val settings: RestaurantSettingsApi,
) : ProductCatalog {

    // ---------------------------------------------------------------- public menu
    fun menu(locale: Locale): Menu {
        val currency = settings.currency()
        val byCategory = products.findMenuProducts().groupBy { it.category }
        return Menu(
            categories = byCategory.map { (category, items) ->
                MenuCategory(
                    id = category.id,
                    name = category.name.localized(locale),
                    products = items.map { p ->
                        MenuProduct(
                            id = p.id,
                            name = p.name.localized(locale),
                            price = p.price.toMoney(currency),
                            available = p.available,
                            allergens = p.allergens,
                            modifierGroups = p.modifierGroups.map { g ->
                                MenuModifierGroup(
                                    id = g.id,
                                    name = g.name.localized(locale),
                                    minSelect = g.minSelect,
                                    maxSelect = g.maxSelect,
                                    options = g.options.filter { it.available }.map { o ->
                                        MenuModifierOption(o.id, o.name.localized(locale), o.priceDelta.toMoney(currency))
                                    },
                                )
                            },
                            description = p.description?.localized(locale),
                            imageUrl = p.imageUrl,
                            prepTimeSec = p.prepTimeSec,
                        )
                    },
                )
            },
        )
    }

    // ---------------------------------------------------------------- ProductCatalog
    override fun priceLines(lines: List<LineSelection>): List<PricedLine> {
        val found = products.findAllById(lines.map { it.productId }.toSet()).associateBy { it.id }
        return lines.map { line ->
            val product = found[line.productId]
            if (product == null || product.archived || !product.available || !product.category.active) {
                throw UnprocessableException("catalog.product_unavailable", product?.name?.get("ro") ?: line.productId)
            }
            val productName = LocalizedText.fromMap(product.name)!!
            val chosen = line.optionIds.distinct().map { optionId ->
                val group = product.modifierGroups.firstOrNull { g -> g.options.any { it.id == optionId } }
                val option = group?.options?.first { it.id == optionId }
                if (group == null || option == null || !option.available) {
                    throw UnprocessableException("catalog.modifier_not_allowed", optionId, productName.ro)
                }
                group to option
            }
            product.modifierGroups.forEach { group ->
                val count = chosen.count { it.first.id == group.id }
                val groupName = LocalizedText.fromMap(group.name)!!.ro
                if (count < group.minSelect) throw UnprocessableException("catalog.modifier_min", group.minSelect, groupName)
                if (count > group.maxSelect) throw UnprocessableException("catalog.modifier_max", group.maxSelect, groupName)
            }
            val vat = settings.vatRate(product.vatRateId) ?: throw UnprocessableException("catalog.invalid_reference", "VAT rate")
            val modifiers = chosen.map { (_, o) -> PricedModifier(o.id, LocalizedText.fromMap(o.name)!!, o.priceDelta) }
            PricedLine(
                productId = product.id,
                name = productName,
                stationId = product.stationId,
                prepTimeSec = product.prepTimeSec,
                vatPercent = vat.percent,
                unitPrice = modifiers.fold(product.price) { acc, m -> acc + m.priceDelta },
                quantity = line.quantity,
                notes = line.notes?.trim()?.ifBlank { null },
                modifiers = modifiers,
            )
        }
    }

    // ---------------------------------------------------------------- categories
    fun listCategories(): List<Category> = categories.findAllByOrderBySortOrderAscIdAsc().map { it.toDto() }

    @Transactional
    fun createCategory(req: CategoryRequest): Category =
        categories.save(CategoryEntity(LocalizedText.of(req.name).toMap(), req.sortOrder ?: 0, req.active ?: true)).toDto()

    @Transactional
    fun updateCategory(id: UUID, req: CategoryRequest): Category {
        val category = categories.findByIdOrNull(id) ?: throw NotFoundException("Category")
        category.name = LocalizedText.of(req.name).toMap()
        req.sortOrder?.let { category.sortOrder = it }
        req.active?.let { category.active = it }
        return category.toDto()
    }

    @Transactional
    fun deleteCategory(id: UUID) {
        val category = categories.findByIdOrNull(id) ?: throw NotFoundException("Category")
        if (products.existsByCategoryId(id)) throw ConflictException("catalog.category_not_empty")
        categories.delete(category)
    }

    // ---------------------------------------------------------------- products
    fun listProducts(categoryId: UUID?): List<Product> {
        val currency = settings.currency()
        val list = if (categoryId == null) products.findAllByOrderBySortOrderAscIdAsc() else products.findAllByCategoryIdOrderBySortOrderAscIdAsc(categoryId)
        return list.map { it.toDto(currency) }
    }

    fun getProduct(id: UUID): Product = (products.findByIdOrNull(id) ?: throw NotFoundException("Product")).toDto(settings.currency())

    @Transactional
    fun createProduct(req: ProductRequest): Product {
        val product = ProductEntity(
            category = category(req.categoryId),
            name = mutableMapOf(),
            price = BigDecimal.ZERO,
            vatRateId = req.vatRateId,
        )
        apply(product, req)
        return products.save(product).toDto(settings.currency())
    }

    @Transactional
    fun updateProduct(id: UUID, req: ProductRequest): Product {
        val product = products.findByIdOrNull(id) ?: throw NotFoundException("Product")
        apply(product, req)
        return product.toDto(settings.currency())
    }

    @Transactional
    fun archiveProduct(id: UUID) {
        val product = products.findByIdOrNull(id) ?: throw NotFoundException("Product")
        product.archived = true
        product.available = false
    }

    private fun apply(product: ProductEntity, req: ProductRequest) {
        if (settings.vatRate(req.vatRateId) == null) throw UnprocessableException("catalog.invalid_reference", "VAT rate")
        if (req.stationId != null && settings.station(req.stationId!!) == null) throw UnprocessableException("catalog.invalid_reference", "Station")
        product.category = category(req.categoryId)
        product.name = LocalizedText.of(req.name).toMap()
        product.description = req.description?.let { LocalizedText.of(it).toMap() }
        product.imageUrl = req.imageUrl?.ifBlank { null }
        product.price = Money.of(req.price)
        product.vatRateId = req.vatRateId
        product.stationId = req.stationId
        product.kind = req.kind?.let { ProductKind.valueOf(it.value) } ?: product.kind
        product.prepTimeSec = req.prepTimeSec ?: product.prepTimeSec
        product.available = req.available ?: product.available
        product.sortOrder = req.sortOrder ?: product.sortOrder
        product.allergens = (req.allergens ?: emptyList()).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().toMutableList()
        val groupIds = req.modifierGroupIds.orEmpty().distinct()
        val found = groups.findAllById(groupIds).associateBy { it.id }
        product.modifierGroups = groupIds.map { found[it] ?: throw UnprocessableException("catalog.invalid_reference", "Modifier group") }.toMutableList()
    }

    private fun category(id: UUID) = categories.findByIdOrNull(id) ?: throw UnprocessableException("catalog.invalid_reference", "Category")

    // ---------------------------------------------------------------- modifier groups
    fun listModifierGroups(): List<AdminModifierGroup> {
        val currency = settings.currency()
        return groups.findAll().sortedBy { it.name["ro"] }.map { it.toDto(currency) }
    }

    @Transactional
    fun createModifierGroup(req: ModifierGroupRequest): AdminModifierGroup {
        val group = ModifierGroupEntity(name = mutableMapOf())
        applyGroup(group, req)
        return groups.save(group).toDto(settings.currency())
    }

    @Transactional
    fun updateModifierGroup(id: UUID, req: ModifierGroupRequest): AdminModifierGroup {
        val group = groups.findByIdOrNull(id) ?: throw NotFoundException("Modifier group")
        applyGroup(group, req)
        return group.toDto(settings.currency())
    }

    @Transactional
    fun deleteModifierGroup(id: UUID) {
        val group = groups.findByIdOrNull(id) ?: throw NotFoundException("Modifier group")
        if (products.isModifierGroupUsed(id)) throw ConflictException("catalog.modifier_group_in_use")
        groups.delete(group)
    }

    private fun applyGroup(group: ModifierGroupEntity, req: ModifierGroupRequest) {
        if (req.minSelect > req.maxSelect) throw UnprocessableException("catalog.modifier_max", req.maxSelect, req.name.ro)
        group.name = LocalizedText.of(req.name).toMap()
        group.minSelect = req.minSelect
        group.maxSelect = req.maxSelect
        val existing = group.options.associateBy { it.id }
        val updated = req.options.mapIndexed { index, o ->
            val option = o.id?.let { existing[it] } ?: ModifierOptionEntity(name = mutableMapOf())
            option.name = LocalizedText.of(o.name).toMap()
            option.priceDelta = Money.of(o.priceDelta ?: "0")
            option.available = o.available ?: true
            option.sortOrder = o.sortOrder ?: index
            option
        }
        group.options.retainAll(updated.toSet())
        updated.filter { it !in group.options }.forEach { group.options.add(it) }
        group.options.sortBy { it.sortOrder }
    }

    // ---------------------------------------------------------------- mapping
    private fun CategoryEntity.toDto() = Category(id, LocalizedText.fromMap(name)!!.toDto(), sortOrder, active)

    private fun ProductEntity.toDto(currency: String) = Product(
        id = id,
        categoryId = category.id,
        name = LocalizedText.fromMap(name)!!.toDto(),
        price = price.toMoney(currency),
        vatRateId = vatRateId,
        kind = ProductKindDto.forValue(kind.name),
        prepTimeSec = prepTimeSec,
        available = available,
        archived = archived,
        sortOrder = sortOrder,
        allergens = allergens,
        modifierGroupIds = modifierGroups.map { it.id },
        description = LocalizedText.fromMap(description)?.toDto(),
        imageUrl = imageUrl,
        stationId = stationId,
    )

    private fun ModifierGroupEntity.toDto(currency: String) = AdminModifierGroup(
        id = id,
        name = LocalizedText.fromMap(name)!!.toDto(),
        minSelect = minSelect,
        maxSelect = maxSelect,
        options = options.map { AdminModifierOption(it.id, LocalizedText.fromMap(it.name)!!.toDto(), it.priceDelta.toMoney(currency), it.available, it.sortOrder) },
    )
}
