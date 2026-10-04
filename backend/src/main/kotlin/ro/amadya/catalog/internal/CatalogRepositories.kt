package ro.amadya.catalog.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface CategoryRepository : JpaRepository<CategoryEntity, UUID> {
    fun findAllByOrderBySortOrderAscIdAsc(): List<CategoryEntity>
}

interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    fun findAllByOrderBySortOrderAscIdAsc(): List<ProductEntity>
    fun findAllByCategoryIdOrderBySortOrderAscIdAsc(categoryId: UUID): List<ProductEntity>
    fun existsByCategoryId(categoryId: UUID): Boolean

    @Query("SELECT p FROM ProductEntity p JOIN FETCH p.category c WHERE p.archived = false AND c.active = true ORDER BY c.sortOrder, c.id, p.sortOrder, p.id")
    fun findMenuProducts(): List<ProductEntity>

    @Query("SELECT count(p) > 0 FROM ProductEntity p JOIN p.modifierGroups g WHERE g.id = :groupId")
    fun isModifierGroupUsed(groupId: UUID): Boolean
}

interface ModifierGroupRepository : JpaRepository<ModifierGroupEntity, UUID>

interface PromotionRepository : JpaRepository<PromotionEntity, UUID> {
    @Query(
        """SELECT p FROM PromotionEntity p WHERE p.active = true
           AND (p.startsAt IS NULL OR p.startsAt <= :now) AND (p.endsAt IS NULL OR p.endsAt > :now)
           ORDER BY p.sortOrder, p.id""",
    )
    fun findActive(now: java.time.Instant): List<PromotionEntity>
}
