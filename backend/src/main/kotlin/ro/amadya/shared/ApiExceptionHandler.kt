package ro.amadya.shared

import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/** Renders every error as RFC 9457 problem+json with a stable `code` and a message in the request locale. */
@RestControllerAdvice
class ApiExceptionHandler(private val messages: MessageSource) : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainException::class)
    fun handleDomain(ex: DomainException): ResponseEntity<ProblemDetail> =
        problem(ex.status, ex.code, message(ex.code, *ex.args))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.FORBIDDEN, "error.forbidden", message("error.forbidden"))

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: ObjectOptimisticLockingFailureException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.CONFLICT, "error.conflict", message("error.conflict"))

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ProblemDetail> {
        log.error("Unhandled error", ex)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", message("error.internal"))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException, headers: HttpHeaders, status: HttpStatusCode, request: WebRequest,
    ): ResponseEntity<Any> {
        val errors = ex.bindingResult.fieldErrors.map { mapOf("field" to it.field, "message" to (it.defaultMessage ?: "invalid")) }
        return validationProblem(errors)
    }

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException, headers: HttpHeaders, status: HttpStatusCode, request: WebRequest,
    ): ResponseEntity<Any> {
        val errors = ex.parameterValidationResults.flatMap { result ->
            result.resolvableErrors.map { mapOf("field" to result.methodParameter.parameterName, "message" to (it.defaultMessage ?: "invalid")) }
        }
        return validationProblem(errors)
    }

    private fun validationProblem(errors: List<Map<String, String?>>): ResponseEntity<Any> {
        val body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message("error.validation"))
        body.setProperty("code", "error.validation")
        body.setProperty("errors", errors)
        return ResponseEntity.badRequest().body(body)
    }

    private fun problem(status: HttpStatus, code: String, detail: String): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatusAndDetail(status, detail)
        body.setProperty("code", code)
        return ResponseEntity.status(status).body(body)
    }

    private fun message(code: String, vararg args: Any?): String =
        messages.getMessage(code, args.map { it ?: "" }.toTypedArray(), code, LocaleContextHolder.getLocale()) ?: code
}
