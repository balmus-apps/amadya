package ro.amadya.shared

import org.springframework.http.HttpStatus

/**
 * Business errors carry a stable [code] (also the i18n message key) and arguments for the localized message.
 * They are rendered as RFC 9457 problem+json by [ApiExceptionHandler].
 */
open class DomainException(
    val status: HttpStatus,
    val code: String,
    vararg val args: Any?,
) : RuntimeException(code)

class NotFoundException(what: String) : DomainException(HttpStatus.NOT_FOUND, "error.not_found", what)

class ConflictException(code: String, vararg args: Any?) : DomainException(HttpStatus.CONFLICT, code, *args)

class UnprocessableException(code: String, vararg args: Any?) : DomainException(HttpStatus.UNPROCESSABLE_CONTENT, code, *args)

class ForbiddenException(code: String = "error.forbidden", vararg args: Any?) : DomainException(HttpStatus.FORBIDDEN, code, *args)

class UnauthorizedException(code: String = "error.unauthorized") : DomainException(HttpStatus.UNAUTHORIZED, code)
