package ro.amadya.identity.internal

import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.contract.model.CreateUserRequest
import ro.amadya.contract.model.Role
import ro.amadya.contract.model.UpdateUserRequest
import ro.amadya.contract.model.User
import ro.amadya.shared.ConflictException
import ro.amadya.shared.NotFoundException
import java.util.UUID
import ro.amadya.contract.model.Locale as LocaleDto

@Service
@Transactional(readOnly = true)
class UserService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authService: AuthService,
) {
    fun get(id: UUID): User = (users.findByIdOrNull(id) ?: throw NotFoundException("User")).toDto()

    fun list(): List<User> = users.findAllByOrderByNameAsc().map { it.toDto() }

    @Transactional
    fun create(req: CreateUserRequest): User {
        val email = req.email.trim().lowercase()
        if (users.existsByEmailIgnoreCase(email)) throw ConflictException("user.email_taken")
        return users.save(
            UserEntity(
                email = email,
                name = req.name.trim(),
                phone = req.phone,
                passwordHash = passwordEncoder.encode(req.password)!!,
                roles = req.roles.map { it.value }.distinct().toMutableList(),
                locale = (req.locale ?: LocaleDto.RO).value,
            ),
        ).toDto()
    }

    @Transactional
    fun update(id: UUID, req: UpdateUserRequest): User {
        val user = users.findByIdOrNull(id) ?: throw NotFoundException("User")
        req.name?.let { user.name = it.trim() }
        req.phone?.let { user.phone = it }
        req.locale?.let { user.locale = it.value }
        req.roles?.let { user.roles = it.map(Role::value).distinct().toMutableList() }
        var revoke = req.roles != null
        req.active?.let { revoke = revoke || (user.active && !it); user.active = it }
        req.password?.let { user.passwordHash = passwordEncoder.encode(it)!!; revoke = true }
        // Role, password or deactivation changes force a fresh login on every device.
        if (revoke) authService.revokeAll(user)
        return user.toDto()
    }

    private fun UserEntity.toDto() = User(
        id = id,
        email = email,
        name = name,
        roles = roles.map(Role::forValue),
        locale = LocaleDto.forValue(locale),
        active = active,
        phone = phone,
    )
}
