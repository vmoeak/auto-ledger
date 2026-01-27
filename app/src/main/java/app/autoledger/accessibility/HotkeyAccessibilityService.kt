package app.autoledger.accessibility

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import android.widget.Toast
import app.autoledger.core.Actions
import app.autoledger.overlay.OverlayConfirmService

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

  private val triggerReceiver = object : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context?, intent: Intent?) {
      val src = intent?.getStringExtra(Actions.EXTRA_TRIGGER_SOURCE) ?: "qs_tile"
      Log.i(TAG, "received broadcast trigger src=$src")
      triggerCapture(src)
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    Log.i(TAG, "service connected")
    try {
      registerReceiver(triggerReceiver, android.content.IntentFilter(Actions.ACTION_TRIGGER_LEDGER))
      Log.i(TAG, "broadcast receiver registered")
    } catch (e: Exception) {
      Log.e(TAG, "registerReceiver failed", e)
    }
  }

  override fun onDestroy() {
    try { unregisterReceiver(triggerReceiver) } catch (_: Exception) {}
    super.onDestroy()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // Keep a fresh cache of current-screen text so QS tile can work even if broadcasts
    // are missed or the service is restarted.
    try {
      val root = rootInActiveWindow
      val extracted = UiTextExtractor.extract(root)
      if (extracted.isNotBlank()) {
        ScreenTextCache.put(this, extracted)
      }
    } catch (_: Exception) {}
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
      val i = Intent(this, OverlayConfirmService::class.java)
      i.putExtra(Actions.EXTRA_TRIGGER_SOURCE, "hotkey")
      i.putExtra(Actions.EXTRA_EXTRACTED_TEXT, extracted)
      startService(i)
    } catch (e: Exception) {
      Log.e(TAG, "startService failed", e)
    }
  }
}
