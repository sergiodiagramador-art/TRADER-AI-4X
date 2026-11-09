package br.com.traderai4x.overlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var web: WebView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12) }
        root.addView(TextView(this).apply { text = "TRADER AI 4X · Painel flutuante"; textSize = 20f })
        root.addView(Button(this).apply {
            text = "Ativar painel sobre outros apps"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    startService(Intent(this@MainActivity, OverlayService::class.java))
                }
            }
        })
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(SignalBridge(), "TraderOverlay")
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        web.loadUrl("https://sergiodiagramador-art.github.io/TRADER-AI-4X/")
    }
    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) startService(Intent(this, OverlayService::class.java))
    }
    inner class SignalBridge {
        @JavascriptInterface fun updateSignal(direction: String, strength: String, detail: String) {
            runOnUiThread { OverlayService.update(direction, strength, detail) }
        }
        @JavascriptInterface fun recordDecision(decision: String) {
            runOnUiThread { OverlayService.setDecision(decision) }
        }
    }
}
