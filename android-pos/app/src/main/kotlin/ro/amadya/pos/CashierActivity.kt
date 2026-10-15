package ro.amadya.pos

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The counter screen: the cashier web app full-screen, with the offline banner and the Device API. */
class CashierActivity : ComponentActivity() {

    private lateinit var config: ServerConfig
    private lateinit var server: String
    private lateinit var webView: WebView
    private lateinit var offline: View
    private lateinit var offlineError: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var webViewGone = false
    private var mainFrameFailed = false
    private var failedUrl: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var cornerPress: Runnable? = null
    private val cornerSize by lazy { CORNER_DP * resources.displayMetrics.density }

    /** While the banner shows, retry every few seconds too: the network may be up while the server is down. */
    private val autoRetry = object : Runnable {
        override fun run() {
            if (offline.visibility == View.VISIBLE) retry()
            handler.postDelayed(this, AUTO_RETRY_MS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ServerConfig(this)
        if (!config.isConfigured) {
            openSetup()
            finish()
            return
        }
        server = config.server

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_cashier)
        hideSystemBars()

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom)
            insets
        }

        offline = findViewById(R.id.offline)
        offlineError = findViewById(R.id.offline_error)
        findViewById<Button>(R.id.offline_retry).setOnClickListener { retry() }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView = findViewById(R.id.web)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // staff-auth keeps the refresh token in localStorage
            mediaPlaybackRequiresUserGesture = false // the "new phone order" sound
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            textZoom = 100
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = CashierClient()
        AmadyaDevice.install(this, webView, server)

        onBackPressedDispatcher.addCallback(this) {
            // Back moves back inside the cashier and never leaves the app.
            if (webView.canGoBack()) webView.goBack()
        }

        webView.loadUrl(config.cashierUrl)
    }

    override fun onStart() {
        super.onStart()
        if (!::webView.isInitialized) return
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post { if (offline.visibility == View.VISIBLE) retry() }
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        networkCallback = callback
        handler.postDelayed(autoRetry, AUTO_RETRY_MS)
    }

    override fun onStop() {
        networkCallback?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        networkCallback = null
        handler.removeCallbacks(autoRetry)
        cancelCornerPress()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized && !webViewGone) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized && !webViewGone) webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::webView.isInitialized && !webViewGone) webView.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() // again after a dialog or a swipe showed the bars
    }

    /** A 3-second press in the top-left corner opens the setup screen. Touches still reach the page. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                if (ev.x <= cornerSize && ev.y <= cornerSize) {
                    cancelCornerPress()
                    cornerPress = Runnable { cornerPress = null; openSetup() }.also { handler.postDelayed(it, CORNER_HOLD_MS) }
                }
            MotionEvent.ACTION_MOVE ->
                if (ev.x > cornerSize * 1.5f || ev.y > cornerSize * 1.5f) cancelCornerPress()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancelCornerPress()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun cancelCornerPress() {
        cornerPress?.let { handler.removeCallbacks(it) }
        cornerPress = null
    }

    private fun openSetup() {
        startActivity(Intent(this, SetupActivity::class.java))
    }

    private fun retry() {
        webView.loadUrl(failedUrl ?: config.cashierUrl)
    }

    private fun showOffline(detail: String) {
        offlineError.text = detail
        offline.visibility = View.VISIBLE
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // onRenderProcessGone is implemented below; the androidx.webkit check misses it on inner classes.
    @SuppressLint("MissingOnRenderProcessGone")
    private inner class CashierClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (ServerUrls.isSameOrigin(uri.toString(), server)) return false
            val intent = when (uri.scheme?.lowercase()) {
                "tel" -> Intent(Intent.ACTION_DIAL, uri)
                "http", "https" -> Intent(Intent.ACTION_VIEW, uri)
                else -> null
            }
            if (intent != null) {
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    // No dialer or browser on this device: stay on the cashier.
                }
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            mainFrameFailed = false
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!mainFrameFailed) {
                failedUrl = null
                offline.visibility = View.GONE
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            mainFrameFailed = true
            failedUrl = request.url.toString()
            showOffline(error.description?.toString().orEmpty())
        }

        /** The WebView's renderer crashed or was killed for memory: start the cashier again instead of crashing. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            webViewGone = true
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            startActivity(
                Intent(this@CashierActivity, CashierActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
            return true
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            // A 502/503 from Caddy while the server restarts is "can't load" too.
            if (!request.isForMainFrame || response.statusCode < 500) return
            mainFrameFailed = true
            failedUrl = request.url.toString()
            showOffline("HTTP ${response.statusCode}")
        }
    }

    private companion object {
        const val CORNER_DP = 48
        const val CORNER_HOLD_MS = 3_000L
        const val AUTO_RETRY_MS = 10_000L
    }
}
