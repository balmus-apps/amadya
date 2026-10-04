package ro.amadya.shared

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.UUID

object Roles {
    const val ADMIN = "ADMIN"
    const val MANAGER = "MANAGER"
    const val WAITER = "WAITER"
    const val KITCHEN = "KITCHEN"
    const val CASHIER = "CASHIER"
    const val CUSTOMER = "CUSTOMER"

    val STAFF = setOf(ADMIN, MANAGER, WAITER, KITCHEN, CASHIER)
}

/** The authenticated caller of the current request, if any (read from the JWT). */
data class Actor(val userId: UUID, val roles: Set<String>) {
    fun hasAny(vararg role: String) = role.any { it in roles }
    val isStaff get() = roles.any { it in Roles.STAFF }

    companion object {
        fun current(): Actor? {
            val auth = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken ?: return null
            val roles = auth.authorities.mapNotNull { it.authority?.removePrefix("ROLE_") }.toSet()
            return Actor(UUID.fromString(auth.token.subject), roles)
        }
    }
}
