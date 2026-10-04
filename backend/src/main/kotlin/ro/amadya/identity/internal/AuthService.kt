package ro.amadya.identity.internal

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.contract.model.TokenResponse
import ro.amadya.shared.AmadyaProperties
import ro.amadya.shared.UnauthorizedException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.HexFormat

@Service
class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtEncoder: JwtEncoder,
    private val props: AmadyaProperties,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    /** Hash compared when the email is unknown, so response time does not reveal which emails exist. */
    private val dummyHash by lazy { passwordEncoder.encode("not-a-real-password")!! }

    @Transactional
    fun login(email: String, password: String): TokenResponse {
        val user = users.findByEmailIgnoreCase(email.trim())
        val ok = if (user == null) {
            passwordEncoder.matches(password, dummyHash)
            false
        } else {
            user.active && passwordEncoder.matches(password, user.passwordHash)
        }
        if (!ok) throw UnauthorizedException("auth.invalid_credentials")
        return issueTokens(user!!)
    }

    /**
     * Rotates the refresh token. Presenting an already revoked token is treated as theft:
     * every refresh token of that user is revoked.
     */
    @Transactional(noRollbackFor = [UnauthorizedException::class])
    fun refresh(rawToken: String): TokenResponse {
        val now = clock.instant()
        val stored = refreshTokens.findByTokenHash(hash(rawToken)) ?: throw UnauthorizedException("auth.invalid_refresh_token")
        if (stored.revokedAt != null) {
            refreshTokens.revokeAllForUser(stored.userId, now)
            throw UnauthorizedException("auth.invalid_refresh_token")
        }
        val user = users.findById(stored.userId).orElse(null)
        if (stored.expiresAt.isBefore(now) || user == null || !user.active) {
            throw UnauthorizedException("auth.invalid_refresh_token")
        }
        stored.revokedAt = now
        return issueTokens(user)
    }

    @Transactional
    fun logout(rawToken: String) {
        refreshTokens.findByTokenHash(hash(rawToken))?.let { if (it.revokedAt == null) it.revokedAt = clock.instant() }
    }

    @Transactional
    fun revokeAll(user: UserEntity) {
        refreshTokens.revokeAllForUser(user.id, clock.instant())
    }

    private fun issueTokens(user: UserEntity): TokenResponse {
        val now = clock.instant()
        val ttl = props.security.accessTokenTtl
        val claims = JwtClaimsSet.builder()
            .issuer("amadya")
            .subject(user.id.toString())
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .claim("roles", user.roles.toList())
            .claim("name", user.name)
            .claim("email", user.email)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue

        val rawRefresh = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        refreshTokens.save(
            RefreshTokenEntity(userId = user.id, tokenHash = hash(rawRefresh), expiresAt = now.plus(props.security.refreshTokenTtl)),
        )
        return TokenResponse(accessToken = accessToken, refreshToken = rawRefresh, tokenType = "Bearer", expiresIn = ttl.toSeconds().toInt())
    }

    private fun hash(raw: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()))
}
