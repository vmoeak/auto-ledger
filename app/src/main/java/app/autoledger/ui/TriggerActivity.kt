package app.autoledger.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import app.autoledger.accessibility.HotkeyAccessibilityService
import app.autoledger.accessibility.ScreenTextCache
import app.autoledger.core.Actions
import app.autoledger.core.CapturePermissionStore
import app.autoledger.overlay.OverlayConfirmService

/**
 * A no-UI trampoline activity used to collapse QS panel reliably on newer Android.
 * It immediately broadcasts a trigger and finishes without animation.
 */
class TriggerActivity : Activity() {
  private val TAG = "AutoLedger/TriggerActivity"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val accessibilityRunning = HotkeyAccessibilityService.isRunning
    Log.i(TAG, "onCreate: accessibilityRunning=$accessibilityRunning")

    if (accessibilityRunning) {
      // Preferred path: notify AccessibilityService (it can extract fresh screen text).
      // Avoid cache fallback here to prevent duplicate triggers.
      try {
        sendBroadcast(
          Intent(Actions.ACTION_TRIGGER_LEDGER)
            .setPackage(packageName)
            .putExtra(Actions.EXTRA_TRIGGER_SOURCE, "qs_tile")
        )
        Log.i(TAG, "broadcasted trigger (accessibility running)")
      } catch (e: Exception) {
        Log.e(TAG, "broadcast failed", e)
        startMediaProjectionCapture("qs_tile_broadcast_failed")
      }
    } else {
      // Fallback path: AccessibilityService isn't running; use cached screen text.
      Log.w(TAG, "AccessibilityService not running, using cache fallback")
      try {
        val (cached, ts) = ScreenTextCache.get(this)
        val ageMs = System.currentTimeMillis() - ts
        if (cached.isNotBlank() && ageMs in 0..15_000) {
          Log.i(TAG, "starting OverlayConfirmService from cache ageMs=$ageMs len=${cached.length}")
          val i = Intent(this, OverlayConfirmService::class.java).apply {
            putExtra(Actions.EXTRA_TRIGGER_SOURCE, "qs_tile_cache")
            putExtra(Actions.EXTRA_EXTRACTED_TEXT, cached)
          }
          startService(i)
        } else {
          Log.w(TAG, "cache empty/stale ageMs=$ageMs len=${cached.length}")
          Toast.makeText(
            this,
            "未获取到页面文本：正在尝试屏幕录制截图",
            Toast.LENGTH_SHORT
          ).show()
          startMediaProjectionCapture("qs_tile_no_accessibility")
        }
      } catch (e: Exception) {
        Log.e(TAG, "fallback start service failed", e)
        startMediaProjectionCapture("qs_tile_fallback_failed")
      }
    }

    finish()
    overridePendingTransition(0, 0)
  }

  private fun startMediaProjectionCapture(reason: String) {
    val resultData = CapturePermissionStore.resultData
    val resultCode = CapturePermissionStore.resultCode
    val next = if (resultData != null && resultCode != null) {
      Intent(this, CaptureActivity::class.java)
    } else {
      Intent(this, ProjectionPermissionActivity::class.java)
    }
    next.putExtra(Actions.EXTRA_TRIGGER_SOURCE, reason)
    try {
      startActivity(next)
      Log.i(TAG, "startMediaProjectionCapture ok reason=$reason")
    } catch (e: Exception) {
      Log.e(TAG, "startMediaProjectionCapture failed", e)
      Toast.makeText(this, "启动截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }
}
