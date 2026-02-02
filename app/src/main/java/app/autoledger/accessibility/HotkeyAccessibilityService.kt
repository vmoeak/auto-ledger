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

  companion object {
    @Volatile
    var isRunning: Boolean = false
      internal set
  }

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

      if (src == "qs_tile") {
        // For QS tile: wait until SystemUI is gone before extracting.
        waitForNonSystemUiThenTrigger(maxWaitMs = 1500L)
      } else {
        handler.postDelayed({ triggerCapture(src) }, 150)
      }
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    isRunning = true
    Log.i(TAG, "service connected")
    try {
      registerReceiver(
        triggerReceiver,
        android.content.IntentFilter(Actions.ACTION_TRIGGER_LEDGER),
        android.content.Context.RECEIVER_NOT_EXPORTED
      )
      Log.i(TAG, "broadcast receiver registered")
    } catch (e: Exception) {
      Log.e(TAG, "registerReceiver failed", e)
    }
  }

  override fun onDestroy() {
    isRunning = false
    try { unregisterReceiver(triggerReceiver) } catch (_: Exception) {}
    super.onDestroy()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // Keep a fresh cache of current-screen text so QS tile can work even if broadcasts
    // are missed or the service is restarted.
    // Skip caching SystemUI/launcher content to avoid polluting cache when QS panel is open.
    try {
      val root = rootInActiveWindow
      val pkg = root?.packageName?.toString().orEmpty()
      val eventType = event?.eventType ?: -1
      val eventPkg = event?.packageName?.toString().orEmpty()
      Log.d(TAG, "onAccessibilityEvent type=0x${Integer.toHexString(eventType)} eventPkg=$eventPkg rootPkg=$pkg rootNull=${root == null}")
      val shouldSkip = pkg.contains("systemui", ignoreCase = true)
                    || pkg.contains("launcher", ignoreCase = true)
                    || pkg == packageName
      if (shouldSkip) return

      val extracted = UiTextExtractor.extract(root)
      if (extracted.isNotBlank()) {
        ScreenTextCache.put(this, extracted)
        Log.d(TAG, "cache updated pkg=$pkg extractedLen=${extracted.length}")
      } else {
        Log.d(TAG, "extracted text blank for pkg=$pkg, skipping cache update")
      }
    } catch (e: Exception) {
      Log.w(TAG, "onAccessibilityEvent error", e)
    }
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

  private fun waitForNonSystemUiThenTrigger(maxWaitMs: Long) {
    val start = System.currentTimeMillis()
    val myPkg = packageName

    fun poll() {
      val now = System.currentTimeMillis()
      val elapsed = now - start
      val root = rootInActiveWindow
      val pkg = root?.packageName?.toString().orEmpty()
      val windowId = root?.windowId ?: -1
      val childCount = root?.childCount ?: -1
      Log.d(TAG, "poll: elapsed=${elapsed}ms pkg=$pkg windowId=$windowId childCount=$childCount rootNull=${root == null}")
      // Skip SystemUI, launcher, and our own app (TriggerActivity may briefly appear)
      val shouldSkip = pkg.contains("systemui", ignoreCase = true)
                    || pkg.contains("launcher", ignoreCase = true)
                    || pkg == myPkg

      if (!shouldSkip && pkg.isNotBlank()) {
        Log.i(TAG, "Target app in foreground, extracting from pkg=$pkg windowId=$windowId childCount=$childCount")
        triggerCapture("qs_tile")
        return
      }

      if (now - start >= maxWaitMs) {
        Log.w(TAG, "Timed out waiting for target app (pkg=$pkg windowId=$windowId). Proceeding anyway.")
        triggerCapture("qs_tile")
        return
      }

      handler.postDelayed({ poll() }, 100)
    }

    handler.postDelayed({ poll() }, 100)
  }

  private fun triggerCapture(reason: String) {
    val now = System.currentTimeMillis()
    if (now - lastTriggerAt < debounceMs) {
      Log.i(TAG, "debounced (reason=$reason elapsed=${now - lastTriggerAt}ms)")
      return
    }
    lastTriggerAt = now

    // Extract text from the CURRENT screen BEFORE launching our UI.
    val root = rootInActiveWindow
    val rootPkg = root?.packageName?.toString().orEmpty()
    val rootClass = root?.className?.toString().orEmpty()
    val rootChildCount = root?.childCount ?: -1
    val rootWindowId = root?.windowId ?: -1
    Log.i(TAG, "triggerCapture reason=$reason rootPkg=$rootPkg rootClass=$rootClass childCount=$rootChildCount windowId=$rootWindowId rootNull=${root == null}")

    // List accessible windows for diagnostics
    try {
      val windowList = windows
      Log.i(TAG, "accessible windows count=${windowList?.size ?: 0}")
      windowList?.forEachIndexed { idx, w ->
        Log.i(TAG, "  window[$idx] id=${w.id} type=${w.type} layer=${w.layer} title=${w.title} pkg=${w.root?.packageName}")
      }
    } catch (e: Exception) {
      Log.w(TAG, "failed to enumerate windows", e)
    }

    val extracted = UiTextExtractor.extract(root)
    Log.i(TAG, "triggerCapture extractedLen=${extracted.length}")
    // WARNING: logs may contain sensitive info from the current screen.
    Log.i(TAG, "extractedText BEGIN\n$extracted\nextractedText END")

    if (extracted.isBlank()) {
      Log.w(TAG, "extracted text is blank for pkg=$rootPkg, NOT starting overlay service")
      Toast.makeText(this, "No readable text on current screen (Accessibility)", Toast.LENGTH_SHORT).show()
      return
    }

    try {
      val i = Intent(this, OverlayConfirmService::class.java)
      i.putExtra(Actions.EXTRA_TRIGGER_SOURCE, "hotkey")
      i.putExtra(Actions.EXTRA_EXTRACTED_TEXT, extracted)
      startService(i)
      Log.i(TAG, "OverlayConfirmService started successfully")
    } catch (e: Exception) {
      Log.e(TAG, "startService(OverlayConfirmService) failed", e)
    }
  }
}
