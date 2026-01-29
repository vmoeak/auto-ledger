package app.autoledger.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import app.autoledger.accessibility.HotkeyAccessibilityService
import app.autoledger.accessibility.ScreenTextCache
import app.autoledger.core.Actions
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

    if (accessibilityRunning) {
      // Preferred path: notify AccessibilityService (it can extract fresh screen text).
      // Don't use cache path when service is running to avoid duplicate triggers.
      try {
        sendBroadcast(
          Intent(Actions.ACTION_TRIGGER_LEDGER)
            .setPackage(packageName)
            .putExtra(Actions.EXTRA_TRIGGER_SOURCE, "qs_tile")
        )
        Log.i(TAG, "broadcasted trigger (accessibility running)")
      } catch (e: Exception) {
        Log.e(TAG, "broadcast failed", e)
      }
    } else {
      // Fallback path: AccessibilityService isn't running, use cached screen text.
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
          Toast.makeText(this, "未获取到页面文本：请确保已开启无障碍，并打开目标页面后再点"记一笔"", Toast.LENGTH_SHORT).show()
        }
      } catch (e: Exception) {
        Log.e(TAG, "fallback start service failed", e)
      }
    }

    finish()
    overridePendingTransition(0, 0)
  }
}
