package ro.amadya.pos

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Build
import android.webkit.WebView
import android.widget.ScrollView
import android.widget.TextView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

/**
 * `window.AmadyaDevice` for the cashier web app: `info()` and `print(text)`, each returning a Promise.
 *
 * It is exposed only to the configured server's origin, in the main frame. Both the message channel and the
 * script that defines `AmadyaDevice` are registered with that origin as the only allowed rule, so pages and frames
 * from any other origin get nothing. `addJavascriptInterface` can't do that: it is visible to every frame.
 *
 * In this change `print` only shows a preview. The next change sends the same call to a real printer.
 */
object AmadyaDevice {

    private const val CHANNEL = "__amadyaDevice"

    /** Returns false (and exposes nothing) on a WebView too old for origin-restricted messaging. */
    @SuppressLint("RequiresFeature") // both features are checked first
    fun install(activity: Activity, webView: WebView, server: String): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            return false
        }
        val rules = setOf(server)
        WebViewCompat.addWebMessageListener(webView, CHANNEL, rules) { _, message, sourceOrigin, isMainFrame, reply ->
            if (isMainFrame && ServerUrls.isSameOrigin(sourceOrigin.toString(), server)) {
                handle(activity, message, reply)
            }
        }
        WebViewCompat.addDocumentStartJavaScript(webView, SHIM, rules)
        return true
    }

    @SuppressLint("RequiresFeature") // only reached through the listener, which needs WEB_MESSAGE_LISTENER
    private fun handle(activity: Activity, message: WebMessageCompat, reply: JavaScriptReplyProxy) {
        val request = try {
            JSONObject(message.data ?: return)
        } catch (_: Exception) {
            return
        }
        val id = request.optInt("id")
        val response = JSONObject().put("id", id)
        when (request.optString("method")) {
            "info" -> response.put("result", info())
            "print" -> {
                showPreview(activity, request.optString("text"))
                response.put("result", JSONObject().put("ok", true).put("preview", true))
            }
            else -> response.put("error", "Unknown method")
        }
        reply.postMessage(response.toString())
    }

    private fun info() = JSONObject()
        .put("appVersion", BuildConfig.VERSION_NAME)
        .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
        .put("androidVersion", Build.VERSION.RELEASE)
        .put("printers", JSONArray())

    private fun showPreview(activity: Activity, text: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val body = TextView(activity).apply {
            this.text = text
            typeface = Typeface.MONOSPACE
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(padding, padding, padding, padding)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.preview_title)
            .setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton(R.string.preview_ok, null)
            .show()
    }

    /** Runs before the page's own scripts, only on the server's origin. Wraps the channel in a Promise API. */
    private val SHIM = """
        (function () {
          var channel = window.$CHANNEL;
          if (!channel || window.AmadyaDevice || window !== window.top) return;
          var nextId = 1;
          var pending = {};
          channel.onmessage = function (event) {
            var msg;
            try { msg = JSON.parse(event.data); } catch (e) { return; }
            var call = pending[msg.id];
            if (!call) return;
            delete pending[msg.id];
            if (msg.error) call.reject(new Error(msg.error)); else call.resolve(msg.result);
          };
          function send(method, args) {
            return new Promise(function (resolve, reject) {
              var id = nextId++;
              pending[id] = { resolve: resolve, reject: reject };
              var request = { id: id, method: method };
              for (var key in args) request[key] = args[key];
              channel.postMessage(JSON.stringify(request));
            });
          }
          Object.defineProperty(window, "AmadyaDevice", {
            value: Object.freeze({
              info: function () { return send("info", {}); },
              print: function (text) { return send("print", { text: String(text) }); }
            }),
            writable: false,
            configurable: false
          });
        })();
    """.trimIndent()
}
