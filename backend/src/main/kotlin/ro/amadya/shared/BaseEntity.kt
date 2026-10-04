package ro.amadya.shared

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/** Common columns: application-assigned UUIDv7 id, audit timestamps and optimistic-lock version. */
@MappedSuperclass
abstract class BaseEntity(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = Ids.newId(),
) {
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    @PrePersist
    protected fun onPersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is BaseEntity && other.javaClass == javaClass && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
