package app.autoledger.accessibility

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import android.widget.Toast
import app.autoledger.core.Actions
import app.autoledger.core.ScreenshotStore
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

    // If the process was killed while a takeScreenshot() callback was pending,
    // the flag survives in SharedPreferences. Retry the screenshot now.
    val pendingReason = consumePendingScreenshot()
    if (pendingReason != null) {
      Log.i(TAG, "service reconnected with pending screenshot (reason=$pendingReason), retrying in 500ms")
      handler.postDelayed({ takeScreenshotFallback(pendingReason, rootInActiveWindow?.packageName?.toString().orEmpty()) }, 500)
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
      Log.w(TAG, "extracted text is blank for pkg=$rootPkg, falling back to screenshot")
      if (!takeScreenshotFallback(reason, rootPkg)) {
        Log.w(TAG, "screenshot fallback not possible, showing manual entry overlay")
        showManualEntry(rootPkg)
      }
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

  // ---- Pending screenshot flag (survives process death via SharedPreferences) ----

  private fun setPendingScreenshot(reason: String) {
    val ok = getSharedPreferences("autoledger_internal", Context.MODE_PRIVATE)
      .edit()
      .putLong("pendingScreenshotTs", System.currentTimeMillis())
      .putString("pendingScreenshotReason", reason)
      .commit()   // commit() is synchronous — survives immediate process death
    Log.d(TAG, "setPendingScreenshot reason=$reason committed=$ok")
  }

  private fun clearPendingScreenshot() {
    getSharedPreferences("autoledger_internal", Context.MODE_PRIVATE)
      .edit()
      .remove("pendingScreenshotTs")
      .remove("pendingScreenshotReason")
      .apply()
  }

  /** Returns the reason if a pending screenshot request is still fresh (< 5s), else null. */
  private fun consumePendingScreenshot(): String? {
    val prefs = getSharedPreferences("autoledger_internal", Context.MODE_PRIVATE)
    val ts = prefs.getLong("pendingScreenshotTs", 0)
    val reason = prefs.getString("pendingScreenshotReason", null)
    // Always clear immediately to prevent infinite retry loops if process keeps dying.
    clearPendingScreenshot()
    if (reason == null || ts == 0L) return null
    val age = System.currentTimeMillis() - ts
    if (age > 5000) {
      Log.d(TAG, "consumePendingScreenshot: expired (age=${age}ms)")
      return null
    }
    Log.i(TAG, "consumePendingScreenshot: reason=$reason age=${age}ms")
    return reason
  }

  // ---- Screenshot fallback ----

  private fun takeScreenshotFallback(reason: String, rootPkg: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      Log.w(TAG, "takeScreenshot requires API 30+, current=${Build.VERSION.SDK_INT} reason=$reason rootPkg=$rootPkg")
      Toast.makeText(this, "当前页面无法读取文字，且系统版本过低不支持截图回退", Toast.LENGTH_SHORT).show()
      return false
    }

    // Save flag BEFORE calling takeScreenshot(). If the process is killed before
    // the callback fires, onServiceConnected() will see this flag and retry.
    setPendingScreenshot(reason)

    Log.i(TAG, "taking screenshot as fallback (reason=$reason rootPkg=$rootPkg)")
    takeScreenshot(
      Display.DEFAULT_DISPLAY,
      mainExecutor,
      object : TakeScreenshotCallback {
        override fun onSuccess(result: ScreenshotResult) {
          clearPendingScreenshot()
          Log.i(TAG, "screenshot success rootPkg=$rootPkg")
          try {
            val hwBuf = result.hardwareBuffer
            val colorSpace = result.colorSpace
            val hardwareBmp = Bitmap.wrapHardwareBuffer(hwBuf, colorSpace)
            hwBuf.close()
            if (hardwareBmp == null) {
              Log.e(TAG, "wrapHardwareBuffer returned null rootPkg=$rootPkg")
              handler.post {
                Toast.makeText(this@HotkeyAccessibilityService, "截图失败", Toast.LENGTH_SHORT).show()
              }
              return
            }
            // Convert to software bitmap so JPEG compression works
            val swBmp = hardwareBmp.copy(Bitmap.Config.ARGB_8888, false)
            hardwareBmp.recycle()
            if (swBmp == null) {
              Log.e(TAG, "bitmap copy to software failed rootPkg=$rootPkg")
              handler.post {
                Toast.makeText(this@HotkeyAccessibilityService, "截图失败", Toast.LENGTH_SHORT).show()
              }
              return
            }
            Log.i(TAG, "screenshot bitmap ${swBmp.width}x${swBmp.height}, starting overlay in screenshot mode rootPkg=$rootPkg")
            ScreenshotStore.put(swBmp)
            val i = Intent(this@HotkeyAccessibilityService, OverlayConfirmService::class.java)
            i.putExtra(Actions.EXTRA_TRIGGER_SOURCE, reason)
            i.putExtra(Actions.EXTRA_SCREENSHOT_MODE, true)
            startService(i)
          } catch (e: Exception) {
            Log.e(TAG, "screenshot post-processing failed rootPkg=$rootPkg", e)
            handler.post {
              Toast.makeText(this@HotkeyAccessibilityService, "截图处理失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
          }
        }

        override fun onFailure(errorCode: Int) {
          clearPendingScreenshot()
          Log.e(TAG, "takeScreenshot failed errorCode=$errorCode reason=$reason rootPkg=$rootPkg")
          logScreenshotErrorDetails(errorCode)
          if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
            || errorCode == ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY
            || errorCode == ERROR_TAKE_SCREENSHOT_INVALID_SCALE) {
            Log.w(TAG, "takeScreenshot failed with retryable errorCode=$errorCode, showing manual entry")
            showManualEntry(rootPkg)
          }
          handler.post {
            Toast.makeText(this@HotkeyAccessibilityService, "截图失败 (错误码: $errorCode)", Toast.LENGTH_SHORT).show()
          }
        }
      }
    )
    return true
  }

  private fun logScreenshotErrorDetails(errorCode: Int) {
    val detail = when (errorCode) {
      ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "interval time too short"
      ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "invalid display"
      ERROR_TAKE_SCREENSHOT_INVALID_SCALE -> "invalid scale"
      ERROR_TAKE_SCREENSHOT_NO_ACCESS -> "no access"
      ERROR_TAKE_SCREENSHOT_NO_HARDWARE_BUFFER -> "no hardware buffer"
      ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "internal error"
      else -> "unknown"
    }
    Log.w(TAG, "takeScreenshot error detail=$detail (code=$errorCode)")
  }

  private fun showManualEntry(rootPkg: String) {
    try {
      val i = Intent(this, OverlayConfirmService::class.java)
      i.putExtra(Actions.EXTRA_TRIGGER_SOURCE, "manual")
      i.putExtra(Actions.EXTRA_MANUAL_MODE, true)
      i.putExtra(Actions.EXTRA_MANUAL_APP, rootPkg)
      i.putExtra(Actions.EXTRA_MANUAL_MESSAGE, "该应用限制无障碍读取，已进入手动录入模式")
      startService(i)
      Log.i(TAG, "manual overlay started for pkg=$rootPkg")
    } catch (e: Exception) {
      Log.e(TAG, "startService(OverlayConfirmService) manual failed", e)
    }
  }
}
