package ro.amadya.audit.internal

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
import ro.amadya.shared.Activity
import ro.amadya.shared.Actor
import ro.amadya.shared.Roles
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

private val WRITES = setOf("POST", "PUT", "PATCH", "DELETE")

/** Token refreshes happen every few minutes in every open app: noise, not an action. */
private val IGNORED_ACTIONS = setOf("refreshToken")

private val APPS = setOf("ADMIN", "WAITER", "KITCHEN")

private fun HttpServletRequest.isWrite() = method in WRITES

/**
 * Records every write request made by staff, in any app, once its response is known (ADR 0011).
 * The action is the operationId of the generated API interface, which is the name of the handler method.
 * Requests that reach no controller (e.g. refused by the URL rules of SecurityConfig) are not seen.
 */
@Configuration
class ActivityRecorder(private val log: ActivityLogService, private val clock: Clock) : HandlerInterceptor, WebMvcConfigurer {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(this)
    }

    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        if (handler !is HandlerMethod || !request.isWrite()) return
        val action = handler.method.name
        if (action in IGNORED_ACTIONS) return
        try {
            entry(request, response, action, ex)?.let(log::record)
        } catch (e: Exception) {
            // The action itself is done; losing its log line must not turn it into an error.
            logger.warn("Could not record activity {} {}", request.method, request.requestURI, e)
        }
    }

    private fun entry(request: HttpServletRequest, response: HttpServletResponse, action: String, ex: Exception?): NewActivity? {
        val note = Activity.current()
        val token = Actor.current()
        val (actorId, actorName, roles) = when {
            note?.actorName != null -> Triple(note.actorId, note.actorName!!, note.actorRoles.orEmpty())
            token != null -> Triple(token.userId, token.name ?: "?", token.roles.toList())
            else -> return null
        }
        // Staff only; a login attempt with an unknown email (no id) is kept, it may be someone guessing passwords.
        if (actorId != null && roles.none { it in Roles.STAFF }) return null

        @Suppress("UNCHECKED_CAST")
        val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String> ?: emptyMap()
        val status = response.status.takeUnless { ex != null && it < 400 } ?: 500
        return NewActivity(
            at = clock.instant(),
            actorId = actorId,
            actorName = actorName,
            actorRoles = roles,
            app = app(request),
            action = action,
            method = request.method,
            path = request.requestURI,
            status = status,
            targetId = note?.targetId ?: pathVariables["id"] ?: pathVariables.values.firstOrNull(),
            target = note?.target,
            detail = note?.detail,
            error = note?.error,
            ip = request.remoteAddr,
        )
    }

    /** The staff apps say who they are; otherwise the endpoint tells (only the waiter app uses /tables). */
    private fun app(request: HttpServletRequest): String {
        request.getHeader(APP_HEADER)?.uppercase()?.takeIf { it in APPS }?.let { return it }
        val path = request.requestURI.removePrefix("/api/v1")
        return when {
            path.startsWith("/admin/") -> "ADMIN"
            path.startsWith("/kitchen/") -> "KITCHEN"
            path.startsWith("/tables/") -> "WAITER"
            else -> "OTHER"
        }
    }

    companion object {
        const val APP_HEADER = "X-Amadya-App"
    }
}

/**
 * Names what a write action returned (an order number, a product name) and keeps the problem code of a refused one,
 * unless the service already described it through [Activity].
 */
@ControllerAdvice
class ActivityResponseAdvice(private val json: JsonMapper) : ResponseBodyAdvice<Any> {

    override fun supports(returnType: MethodParameter, converterType: Class<out org.springframework.http.converter.HttpMessageConverter<*>>) = true

    override fun beforeBodyWrite(
        body: Any?, returnType: MethodParameter, contentType: MediaType, converterType: Class<out org.springframework.http.converter.HttpMessageConverter<*>>,
        request: ServerHttpRequest, response: ServerHttpResponse,
    ): Any? {
        if (body == null || (request as? ServletServerHttpRequest)?.servletRequest?.isWrite() != true) return body
        val note = Activity.current()
        if (body is ProblemDetail) {
            Activity.failed(body.properties?.get("code")?.toString() ?: body.title)
            return body
        }
        if (note?.target != null) return body
        runCatching { json.valueToTree<JsonNode>(body) }.getOrNull()?.takeIf { it.isObject }?.let { node ->
            Activity.describe(target = label(node), targetId = if (note?.targetId == null) node.path("id").stringOrNull() else null)
        }
        return body
    }

    /** The field people know a record by: order / document number, table, name, title, code or email. */
    private fun label(node: JsonNode): String? =
        LABELS.firstNotNullOfOrNull { field ->
            val value = node.path(field)
            if (value.isObject) value.path("ro").stringOrNull() else value.stringOrNull()
        }

    private fun JsonNode.stringOrNull(): String? = if (isString || isNumber) asString().ifBlank { null } else null

    private companion object {
        val LABELS = listOf("number", "orderNumber", "tableLabel", "label", "name", "title", "code", "email")
    }
}
