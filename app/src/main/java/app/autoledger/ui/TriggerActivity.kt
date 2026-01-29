package app.autoledger.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import app.autoledger.accessibility.ScreenTextCache
import app.autoledger.core.Actions
import app.autoledger.overlay.OverlayConfirmService

/**
 * A no-UI trampoline activity used to collapse QS panel reliably on newer Android.
 * It uses cached screen text from AccessibilityService and launches OverlayConfirmService.
 */
class TriggerActivity : Activity() {
  private val TAG = "AutoLedger/TriggerActivity"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Delay slightly to let QS panel collapse animation complete
    Handler(Looper.getMainLooper()).postDelayed({
      launchOverlay()
      finish()
      overridePendingTransition(0, 0)
    }, 300)
  }

  private fun launchOverlay() {
    try {
      val (text, ts) = ScreenTextCache.get(this)
      val ageMs = System.currentTimeMillis() - ts

      Log.i(TAG, "cached text len=${text.length}, age=${ageMs}ms")

      // Accept cache if less than 10 seconds old
      if (text.isBlank() || ageMs > 10_000) {
        Toast.makeText(this, "No recent screen text. Please stay on payment screen longer.", Toast.LENGTH_SHORT).show()
        return
      }

      val i = Intent(this, OverlayConfirmService::class.java)
      i.putExtra(Actions.EXTRA_TRIGGER_SOURCE, "qs_tile")
      i.putExtra(Actions.EXTRA_EXTRACTED_TEXT, text)
      startService(i)
      Log.i(TAG, "launched OverlayConfirmService")
    } catch (e: Exception) {
      Log.e(TAG, "launchOverlay failed", e)
      Toast.makeText(this, "Failed to launch overlay", Toast.LENGTH_SHORT).show()
    }
  }
}
