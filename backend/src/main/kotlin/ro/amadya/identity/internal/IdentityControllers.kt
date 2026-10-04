package ro.amadya.identity.internal

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.IdentityApi
import ro.amadya.contract.api.UsersApi
import ro.amadya.contract.model.CreateUserRequest
import ro.amadya.contract.model.LoginRequest
import ro.amadya.contract.model.RefreshRequest
import ro.amadya.contract.model.TokenResponse
import ro.amadya.contract.model.UpdateUserRequest
import ro.amadya.contract.model.User
import ro.amadya.shared.Actor
import ro.amadya.shared.UnauthorizedException
import java.util.UUID

@RestController
class IdentityController(private val auth: AuthService, private val users: UserService) : IdentityApi {

    override fun login(loginRequest: LoginRequest): ResponseEntity<TokenResponse> =
        ResponseEntity.ok(auth.login(loginRequest.email, loginRequest.password))

    override fun refreshToken(refreshRequest: RefreshRequest): ResponseEntity<TokenResponse> =
        ResponseEntity.ok(auth.refresh(refreshRequest.refreshToken))

    override fun logout(refreshRequest: RefreshRequest): ResponseEntity<Unit> {
        auth.logout(refreshRequest.refreshToken)
        return ResponseEntity.noContent().build()
    }

    override fun getCurrentUser(): ResponseEntity<User> {
        val actor = Actor.current() ?: throw UnauthorizedException()
        return ResponseEntity.ok(users.get(actor.userId))
    }
}

@RestController
@PreAuthorize("hasRole('ADMIN')")
class UsersController(private val users: UserService) : UsersApi {

    override fun listUsers(): ResponseEntity<List<User>> = ResponseEntity.ok(users.list())

    override fun createUser(createUserRequest: CreateUserRequest): ResponseEntity<User> =
        ResponseEntity.status(HttpStatus.CREATED).body(users.create(createUserRequest))

    override fun updateUser(id: UUID, updateUserRequest: UpdateUserRequest): ResponseEntity<User> =
        ResponseEntity.ok(users.update(id, updateUserRequest))
}
