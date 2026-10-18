package com.avaruk.kettlebellcounter

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * A thin shell around the scoreboard web pages. Two screens:
 *  - setup: enter the server address as "ip:port" (e.g. 192.168.1.10:8080), it is remembered;
 *  - web: the site opens in a WebView; its login page picks the remote by PIN (team judge /controller, timekeeper /timer),
 *    and the volume buttons are forwarded to that page as window.onVolume('up' | 'down').
 */
class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("kb", MODE_PRIVATE) }
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()   // no title bar with the app name
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Back goes through the page history; on the first page it opens a small menu (reload / change address / exit)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else if (w != null) showMenu() else finish()
            }
        })

        val saved = prefs.getString(KEY_SERVER, null)
        if (saved != null) openWeb(saved) else showSetup(null)
    }

    // ---------- Setup screen ----------
    private fun showSetup(error: String?) {
        web?.destroy()
        web = null

        val pad = (24 * resources.displayMetrics.density).toInt()
        val title = TextView(this).apply {
            text = "Адрес сервера табло"
            textSize = 22f
        }
        val hint = TextView(this).apply {
            text = "IP-адрес и порт, как в консоли сервера, например 192.168.1.10:8080. Если порт не указан, используется 80."
            textSize = 14f
            setPadding(0, pad / 3, 0, pad / 2)
        }
        val input = EditText(this).apply {
            setText(prefs.getString(KEY_SERVER, "") ?: "")
            this.hint = "192.168.1.10:80"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine()
        }
        val err = TextView(this).apply {
            text = error ?: ""
            setTextColor(Color.parseColor("#ff6b6b"))
            setPadding(0, pad / 3, 0, 0)
        }
        val connect = Button(this).apply { text = "Подключиться" }

        val go = {
            val server = normalize(input.text.toString())
            if (server == null) err.text = "Неверный адрес. Пример: 192.168.1.10:8080"
            else {
                prefs.edit().putString(KEY_SERVER, server).apply()
                openWeb(server)
            }
        }
        connect.setOnClickListener { go() }
        input.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_GO) { go(); true } else false }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(title); addView(hint); addView(input); addView(err); addView(connect)
        })
    }

    // ---------- Web screen ----------
    @SuppressLint("SetJavaScriptEnabled")
    private fun openWeb(server: String) {
        web?.destroy()
        val w = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true   // the page remembers the PIN in localStorage
            setBackgroundColor(Color.BLACK)
            webViewClient = object : WebViewClient() {
                // Main page failed to load (wrong address, server is off, no network) — back to the setup screen
                override fun onReceivedError(view: WebView, request: WebResourceRequest, e: WebResourceError) {
                    if (request.isForMainFrame) showSetup("Не удалось подключиться к $server. Проверьте адрес, Wi-Fi и что сервер запущен.")
                }
            }
            loadUrl("http://$server/")
        }
        web = w
        setContentView(w)
    }

    // Volume buttons go to the page instead of changing the system volume
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val w = web
        if (w != null && (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP || e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                val dir = if (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP) "up" else "down"
                w.evaluateJavascript("window.onVolume && window.onVolume('$dir')", null)
            }
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    // ---------- Menu (Back on the first page) ----------
    private fun showMenu() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Обновить", "Сменить адрес сервера", "Выйти")) { _, which ->
                when (which) {
                    0 -> web?.reload()
                    1 -> showSetup(null)
                    else -> finish()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroy() {
        web?.destroy()
        super.onDestroy()
    }

    /** "http://192.168.1.10:8080/abc" -> "192.168.1.10:8080"; null if the address is not valid */
    private fun normalize(raw: String): String? {
        val s = raw.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
        val m = Regex("^([A-Za-z0-9.-]+)(?::(\\d{1,5}))?$").matchEntire(s) ?: return null
        val port = m.groupValues[2]
        if (port.isNotEmpty() && port.toInt() !in 1..65535) return null
        return s
    }

    companion object {
        private const val KEY_SERVER = "server"
    }
}