package ro.amadya.identity.internal

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ro.amadya.shared.Roles
import java.security.SecureRandom
import java.util.Base64

/**
 * Creates the first ADMIN on an empty install from AMADYA_ADMIN_EMAIL / AMADYA_ADMIN_PASSWORD.
 * When no password is given, a random one is generated and logged once.
 */
@Component
class AdminBootstrap(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @param:Value("\${amadya.bootstrap.admin-email:}") private val email: String,
    @param:Value("\${amadya.bootstrap.admin-password:}") private val password: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (email.isBlank() || users.anyAdminExists()) return
        val pwd = password.ifBlank {
            ByteArray(12).also(SecureRandom()::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
                .also { log.warn("Generated password for bootstrap admin {}: {} (change it after first login)", email, it) }
        }
        users.save(
            UserEntity(email = email.lowercase(), name = "Administrator", passwordHash = passwordEncoder.encode(pwd)!!, roles = mutableListOf(Roles.ADMIN)),
        )
        log.info("Bootstrap admin {} created", email)
    }
}
