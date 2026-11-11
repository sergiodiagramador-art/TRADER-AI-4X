package br.com.traderai4x.overlay

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        handleIncomingImage(intent)
    }

    private fun buildScreen() {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingImage(intent)
    }

    private fun handleIncomingImage(source: Intent?) {
        if (source?.action != Intent.ACTION_SEND) return
        val uri = source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            ?: source.clipData?.getItemAt(0)?.uri
        if (uri == null) {
            showReceiveError("O Capture não enviou o endereço da imagem.")
            return
        }
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap == null) {
                showReceiveError("O Overlay recebeu o arquivo, mas não conseguiu abrir a imagem.")
                return
            }
            val preview = ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (resources.displayMetrics.heightPixels * 0.62f).toInt()
                )
            }
            AlertDialog.Builder(this)
                .setTitle("Captura recebida do Capture")
                .setMessage("A imagem da tela da Bullex chegou ao Overlay.")
                .setView(preview)
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            showReceiveError("Falha ao abrir a captura: ${e.javaClass.simpleName}")
        }
    }

    private fun showReceiveError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Não foi possível receber a captura")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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