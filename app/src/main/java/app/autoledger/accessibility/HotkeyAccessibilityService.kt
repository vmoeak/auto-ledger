package app.autoledger.accessibility

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import app.autoledger.ui.CaptureActivity
import android.widget.Toast

/**
 * Global hotkey: long-press VOLUME_UP to trigger capture.
 *
 * Requires user to enable Accessibility for Auto Ledger.
 */
class HotkeyAccessibilityService : AccessibilityService() {

  private val TAG = "AutoLedger/Hotkey"

  private val handler = Handler(Looper.getMainLooper())

  private var volumeUpDownAt: Long = 0L
  private var longPressTriggered: Boolean = false
  private var lastTriggerAt: Long = 0L

  private val longPressMs = 650L
  private val debounceMs = 2500L

  private val longPressRunnable = Runnable {
    // Still held?
    if (volumeUpDownAt != 0L && !longPressTriggered) {
      longPressTriggered = true
      triggerCapture("longpress")
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    Log.i(TAG, "service connected")
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // not used
  }

  override fun onInterrupt() {
    // not used
  }

  override fun onKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false

    when (event.action) {
      KeyEvent.ACTION_DOWN -> {
        // First down only
        if (event.repeatCount == 0) {
          volumeUpDownAt = System.currentTimeMillis()
          longPressTriggered = false
          handler.removeCallbacks(longPressRunnable)
          handler.postDelayed(longPressRunnable, longPressMs)
          Log.i(TAG, "VOLUME_UP down")
        }
        // Do not consume yet; only consume once long-press confirmed.
        return longPressTriggered
      }

      KeyEvent.ACTION_UP -> {
        Log.i(TAG, "VOLUME_UP up (triggered=$longPressTriggered)")
        handler.removeCallbacks(longPressRunnable)
        volumeUpDownAt = 0L
        // If we triggered on long-press, consume the UP so user doesn’t also change volume.
        return longPressTriggered
      }
    }

    return false
  }

  private fun triggerCapture(reason: String) {
    val now = System.currentTimeMillis()
    if (now - lastTriggerAt < debounceMs) {
      Log.i(TAG, "debounced")
      return
    }
    lastTriggerAt = now

    // Extract text from the CURRENT screen BEFORE launching our UI.
    val root = rootInActiveWindow
    val extracted = UiTextExtractor.extract(root)
    Log.i(TAG, "triggerCapture reason=$reason extractedLen=${extracted.length}")
    // WARNING: logs may contain sensitive info from the current screen.
    Log.i(TAG, "extractedText BEGIN\n$extracted\nextractedText END")

    if (extracted.isBlank()) {
      Toast.makeText(this, "No readable text on current screen (Accessibility)", Toast.LENGTH_SHORT).show()
      return
    }

    try {
      val i = Intent(this, CaptureActivity::class.java)
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      i.putExtra("extractedText", extracted)
      startActivity(i)
    } catch (e: Exception) {
      Log.e(TAG, "startActivity failed", e)
    }
  }
}
