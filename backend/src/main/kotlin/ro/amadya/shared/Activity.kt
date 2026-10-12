package ro.amadya.shared

import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import java.util.UUID

/**
 * Adds to the activity-log entry of the current request, which the audit module writes once the response is done.
 * Every staff write action is logged without this; a service calls it to say what was acted on and what changed,
 * or who is acting when the request carries no token (login, logout).
 * Outside a request (scheduled jobs, event listeners) the calls do nothing.
 */
object Activity {
    class Note {
        var target: String? = null
        var targetId: String? = null
        var detail: String? = null
        var error: String? = null
        var actorId: UUID? = null
        var actorName: String? = null
        var actorRoles: List<String>? = null
    }

    private const val ATTRIBUTE = "ro.amadya.activity"

    /** Names the record acted on (e.g. "Pizza Margherita", "A-042") and, optionally, what changed. */
    fun describe(target: String? = null, detail: String? = null, targetId: Any? = null) {
        val note = note() ?: return
        target?.let { note.target = it }
        detail?.let { note.detail = it }
        targetId?.let { note.targetId = it.toString() }
    }

    /** The problem code of a refused or failed action. */
    fun failed(code: String?) {
        note()?.error = code
    }

    /** The person acting on a request without a token; `id` is null for a login with an unknown email. */
    fun actingAs(id: UUID?, name: String, roles: Collection<String>) {
        val note = note() ?: return
        note.actorId = id
        note.actorName = name
        note.actorRoles = roles.toList()
    }

    fun current(): Note? =
        RequestContextHolder.getRequestAttributes()?.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) as? Note

    private fun note(): Note? {
        val attributes = RequestContextHolder.getRequestAttributes() ?: return null
        return current() ?: Note().also { attributes.setAttribute(ATTRIBUTE, it, RequestAttributes.SCOPE_REQUEST) }
    }
}
