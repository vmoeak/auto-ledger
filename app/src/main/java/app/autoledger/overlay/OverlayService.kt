package app.autoledger.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import app.autoledger.R
import app.autoledger.ui.CaptureActivity

class OverlayService : Service() {

  private lateinit var wm: WindowManager
  private var view: View? = null

  override fun onCreate() {
    super.onCreate()
    isRunning = true
    wm = getSystemService(WINDOW_SERVICE) as WindowManager

    val btn = ImageButton(this)
    btn.setImageResource(android.R.drawable.ic_input_add)
    btn.setBackgroundColor(0x55FFFFFF)
    btn.setOnClickListener {
      val i = Intent(this, CaptureActivity::class.java)
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      startActivity(i)
    }

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    )
    params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
    params.x = 24
    params.y = 0

    view = btn
    wm.addView(btn, params)
  }

  override fun onDestroy() {
    super.onDestroy()
    isRunning = false
    view?.let { wm.removeView(it) }
    view = null
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    @Volatile var isRunning: Boolean = false
  }
}
