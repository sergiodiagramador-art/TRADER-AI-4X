package br.com.traderai4x.overlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.view.ViewGroup
import java.io.InputStream
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
        handleSharedImage(intent)
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleSharedImage(intent)
        }
    }

    private fun handleSharedImage(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: return
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap == null) {
                android.widget.Toast.makeText(this, "Não foi possível abrir a imagem recebida.", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            val preview = ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.BLACK)
            }
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = "Imagem recebida. Esta etapa apenas confirma a transferência; ainda não extrai preços nem gera sinais."
                    textSize = 14f
                    setPadding(16, 12, 16, 12)
                })
            }
            AlertDialog.Builder(this)
                .setTitle("Captura recebida")
                .setView(container)
                .setPositiveButton("Fechar") { _, _ -> bitmap.recycle() }
                .setOnCancelListener { bitmap.recycle() }
                .show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Falha ao receber a captura: " + e.javaClass.simpleName, android.widget.Toast.LENGTH_LONG).show()
        }
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
