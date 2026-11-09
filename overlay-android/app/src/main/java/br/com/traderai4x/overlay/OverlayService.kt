package br.com.traderai4x.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private lateinit var signal: TextView
    private lateinit var meta: TextView
    private lateinit var status: TextView
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildPanel()
    }
    private fun buildPanel() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            setBackgroundColor(Color.rgb(13, 24, 40))
        }
        signal = TextView(this).apply { text = "AGUARDANDO SINAL"; textSize = 19f; setTextColor(Color.WHITE) }
        meta = TextView(this).apply { text = "Abra a análise no TRADER AI 4X"; textSize = 12f; setTextColor(Color.LTGRAY) }
        status = TextView(this).apply { text = "Entrada não registrada"; textSize = 11f; setTextColor(Color.YELLOW) }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text = "ENTREI"; setOnClickListener { status.text = "Entrada registrada · aguardando resultado"; status.setTextColor(Color.GREEN) } }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply { text = "NÃO ENTREI"; setOnClickListener { status.text = "Sinal ignorado"; status.setTextColor(Color.LTGRAY) } }, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(signal); box.addView(meta); box.addView(status); box.addView(actions)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 12; y = 140 }
        signal.setOnTouchListener(object : android.view.View.OnTouchListener {
            private var sx = 0f; private var sy = 0f; private var ox = 0; private var oy = 0
            override fun onTouch(v: android.view.View, e: android.view.MotionEvent): Boolean {
                when (e.action) {
                    android.view.MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=p.x; oy=p.y; return true }
                    android.view.MotionEvent.ACTION_MOVE -> { p.x=ox+(sx-e.rawX).toInt(); p.y=oy+(e.rawY-sy).toInt(); wm.updateViewLayout(box,p); return true }
                    android.view.MotionEvent.ACTION_UP -> return true
                }
                return false
            }
        })
        panel = box; params = p
        try { wm.addView(box, p) } catch (_: Exception) { stopSelf() }
    }
    override fun onDestroy() { panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }; panel=null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile private var instance: OverlayService? = null
        fun update(direction: String, strength: String, detail: String) {
            instance?.let { s ->
                s.signal.text = if (direction == "NEUTRO") "AGUARDAR" else direction
                s.meta.text = "$strength · $detail"
                s.signal.setTextColor(when(direction) { "CALL" -> Color.rgb(53,211,154); "PUT" -> Color.rgb(255,102,125); else -> Color.YELLOW })
                s.status.text = "Entrada não registrada"
            }
        }
        fun setDecision(decision: String) { instance?.status?.text = decision }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { instance = this; return START_STICKY }
}
