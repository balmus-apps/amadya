package ro.amadya.identity.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ro.amadya.shared.BaseEntity
import ro.amadya.shared.Ids
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "app_user")
class UserEntity(
    var email: String,
    var name: String,
    var phone: String? = null,
    @Column(name = "password_hash") var passwordHash: String,
    @JdbcTypeCode(SqlTypes.ARRAY) var roles: MutableList<String>,
    var locale: String = "ro",
    var active: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "refresh_token")
class RefreshTokenEntity(
    @Id val id: UUID = Ids.newId(),
    @Column(name = "user_id") val userId: UUID,
    @Column(name = "token_hash") val tokenHash: String,
    @Column(name = "expires_at") val expiresAt: Instant,
    @Column(name = "revoked_at") var revokedAt: Instant? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
)

interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEmailIgnoreCase(email: String): UserEntity?
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun findAllByOrderByNameAsc(): List<UserEntity>

    @Query(value = "SELECT count(*) > 0 FROM app_user WHERE 'ADMIN' = ANY(roles)", nativeQuery = true)
    fun anyAdminExists(): Boolean
}

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
    fun revokeAllForUser(userId: UUID, now: Instant): Int
}
