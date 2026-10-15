package ro.amadya.pos

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Pure URL rules for the server address. No Android types, so they run in plain JVM unit tests. */
object ServerUrls {

    sealed interface Parsed {
        data class Ok(val server: String) : Parsed
        data class Error(val reason: Reason) : Parsed
    }

    enum class Reason { EMPTY, INVALID, HTTP_NOT_ALLOWED }

    /**
     * Normalizes what the user typed into `scheme://host[:port]`.
     * - No scheme means `https://`.
     * - A trailing `/` or `/cashier` path is dropped. Any other path, query or fragment is invalid.
     * - `http://` is refused unless [allowHttp] is set (debug builds only).
     */
    fun parse(input: String, allowHttp: Boolean): Parsed {
        val text = input.trim()
        if (text.isEmpty()) return Parsed.Error(Reason.EMPTY)
        val withScheme = if (text.contains("://")) text else "https://$text"
        val uri = try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return Parsed.Error(Reason.INVALID)
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        if (host.isNullOrEmpty() || uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) {
            return Parsed.Error(Reason.INVALID)
        }
        val path = uri.rawPath.orEmpty().trimEnd('/')
        if (path.isNotEmpty() && path != "/cashier") return Parsed.Error(Reason.INVALID)
        when (scheme) {
            "https" -> Unit
            "http" -> if (!allowHttp) return Parsed.Error(Reason.HTTP_NOT_ALLOWED)
            else -> return Parsed.Error(Reason.INVALID)
        }
        return Parsed.Ok(origin(scheme, host, uri.port))
    }

    /** The page the app opens: the cashier web app under the server. */
    fun cashierUrl(server: String) = "$server/cashier/"

    /** A public endpoint of the contract, used by "Test connection". */
    fun testUrl(server: String) = "$server/api/v1/settings/public"

    /** `scheme://host[:port]` of a URL, with default ports left out; null for anything that isn't http(s). */
    fun originOf(url: String): String? {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        return origin(scheme, host, uri.port)
    }

    /** True when [url] belongs to the configured [server] (same scheme, host and port). */
    fun isSameOrigin(url: String, server: String): Boolean {
        val origin = originOf(url) ?: return false
        return origin == originOf(server)
    }

    private fun origin(scheme: String, host: String, port: Int): String {
        val defaultPort = if (scheme == "https") 443 else 80
        val hostPart = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return if (port == -1 || port == defaultPort) "$scheme://$hostPart" else "$scheme://$hostPart:$port"
    }
}
