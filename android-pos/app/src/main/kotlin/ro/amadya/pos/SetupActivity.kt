package ro.amadya.pos

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.net.HttpURLConnection
import java.net.URL

/** Server address, "Test connection" and the app version. Opens on first launch and from the cashier's corner. */
class SetupActivity : ComponentActivity() {

    private lateinit var config: ServerConfig
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var testRun = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ServerConfig(this)
        setContentView(R.layout.activity_setup)

        // Edge-to-edge (target SDK 35): keep the form clear of the system bars and the keyboard.
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        input = findViewById(R.id.server)
        status = findViewById(R.id.status)
        input.setText(config.server)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) testConnection()
            false
        }
        findViewById<View>(R.id.dev_hint).visibility = if (ServerConfig.allowHttp) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.version).text = getString(R.string.setup_version, BuildConfig.VERSION_NAME)
        findViewById<Button>(R.id.test).setOnClickListener { testConnection() }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
    }

    /** The typed address as a server origin, or null after showing why it is refused. */
    private fun parsedServer(): String? =
        when (val parsed = ServerUrls.parse(input.text.toString(), ServerConfig.allowHttp)) {
            is ServerUrls.Parsed.Ok -> parsed.server
            is ServerUrls.Parsed.Error -> {
                status.text = getString(
                    when (parsed.reason) {
                        ServerUrls.Reason.EMPTY -> R.string.setup_error_empty
                        ServerUrls.Reason.INVALID -> R.string.setup_error_invalid
                        ServerUrls.Reason.HTTP_NOT_ALLOWED -> R.string.setup_error_http
                    },
                )
                null
            }
        }

    private fun testConnection() {
        val server = parsedServer() ?: return
        val run = ++testRun
        status.text = getString(R.string.setup_testing)
        Thread {
            val message = try {
                val connection = URL(ServerUrls.testUrl(server)).openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                try {
                    val code = connection.responseCode
                    if (code in 200..299) getString(R.string.setup_ok, code) else getString(R.string.setup_http_status, code)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                getString(R.string.setup_failed, e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                // Only the latest test reports, and only while the screen is still open.
                if (run == testRun && !isFinishing && !isDestroyed) status.text = message
            }
        }.start()
    }

    private fun save() {
        val server = parsedServer() ?: return
        config.save(server)
        // Restart the cashier so the WebView and the Device API use the new origin.
        startActivity(
            Intent(this, CashierActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
    }
}
