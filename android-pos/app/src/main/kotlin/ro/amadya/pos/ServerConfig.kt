package ro.amadya.pos

import android.content.Context

/** The server address kept on the device. Defaults to production until the setup screen saves another one. */
class ServerConfig(context: Context) {
    private val prefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)

    /** `scheme://host[:port]`, always valid for this build type. */
    val server: String
        get() {
            val saved = prefs.getString(KEY_SERVER, null)
            val parsed = saved?.let { ServerUrls.parse(it, allowHttp) }
            return (parsed as? ServerUrls.Parsed.Ok)?.server ?: BuildConfig.DEFAULT_SERVER
        }

    /** False until the setup screen has been saved once (first launch). */
    val isConfigured: Boolean get() = prefs.contains(KEY_SERVER)

    val cashierUrl: String get() = ServerUrls.cashierUrl(server)

    fun save(server: String) {
        prefs.edit().putString(KEY_SERVER, server).apply()
    }

    companion object {
        private const val KEY_SERVER = "server"

        /** Plain HTTP only in debug builds, to reach the laptop's dev server on the Wi-Fi. */
        val allowHttp: Boolean get() = BuildConfig.DEBUG
    }
}
