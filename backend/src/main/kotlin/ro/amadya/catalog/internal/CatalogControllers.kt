package ro.amadya.catalog.internal

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.CatalogAdminApi
import ro.amadya.contract.api.CatalogApi
import ro.amadya.contract.model.AdminModifierGroup
import ro.amadya.contract.model.Category
import ro.amadya.contract.model.CategoryRequest
import ro.amadya.contract.model.Menu
import ro.amadya.contract.model.ModifierGroupRequest
import ro.amadya.contract.model.Product
import ro.amadya.contract.model.ProductRequest
import java.util.UUID

@RestController
class MenuController(private val catalog: CatalogService) : CatalogApi {
    override fun getMenu(acceptLanguage: String?): ResponseEntity<Menu> = ResponseEntity.ok(catalog.menu(LocaleContextHolder.getLocale()))
}

@RestController
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
class CatalogAdminController(private val catalog: CatalogService) : CatalogAdminApi {

    override fun listCategories(): ResponseEntity<List<Category>> = ResponseEntity.ok(catalog.listCategories())

    override fun createCategory(categoryRequest: CategoryRequest): ResponseEntity<Category> =
        ResponseEntity.status(HttpStatus.CREATED).body(catalog.createCategory(categoryRequest))

    override fun updateCategory(id: UUID, categoryRequest: CategoryRequest): ResponseEntity<Category> =
        ResponseEntity.ok(catalog.updateCategory(id, categoryRequest))

    override fun deleteCategory(id: UUID): ResponseEntity<Unit> {
        catalog.deleteCategory(id)
        return ResponseEntity.noContent().build()
    }

    override fun listProducts(categoryId: UUID?): ResponseEntity<List<Product>> = ResponseEntity.ok(catalog.listProducts(categoryId))

    override fun getProduct(id: UUID): ResponseEntity<Product> = ResponseEntity.ok(catalog.getProduct(id))

    override fun createProduct(productRequest: ProductRequest): ResponseEntity<Product> =
        ResponseEntity.status(HttpStatus.CREATED).body(catalog.createProduct(productRequest))

    override fun updateProduct(id: UUID, productRequest: ProductRequest): ResponseEntity<Product> =
        ResponseEntity.ok(catalog.updateProduct(id, productRequest))

    override fun deleteProduct(id: UUID): ResponseEntity<Unit> {
        catalog.archiveProduct(id)
        return ResponseEntity.noContent().build()
    }

    override fun listModifierGroups(): ResponseEntity<List<AdminModifierGroup>> = ResponseEntity.ok(catalog.listModifierGroups())

    override fun createModifierGroup(modifierGroupRequest: ModifierGroupRequest): ResponseEntity<AdminModifierGroup> =
        ResponseEntity.status(HttpStatus.CREATED).body(catalog.createModifierGroup(modifierGroupRequest))

    override fun updateModifierGroup(id: UUID, modifierGroupRequest: ModifierGroupRequest): ResponseEntity<AdminModifierGroup> =
        ResponseEntity.ok(catalog.updateModifierGroup(id, modifierGroupRequest))

    override fun deleteModifierGroup(id: UUID): ResponseEntity<Unit> {
        catalog.deleteModifierGroup(id)
        return ResponseEntity.noContent().build()
    }
}
