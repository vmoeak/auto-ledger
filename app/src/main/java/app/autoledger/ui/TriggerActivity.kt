package app.autoledger.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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

    // 1) Preferred path: notify AccessibilityService (it can extract fresh screen text).
    try {
      sendBroadcast(
        Intent(Actions.ACTION_TRIGGER_LEDGER)
          .setPackage(packageName)
          .putExtra(Actions.EXTRA_TRIGGER_SOURCE, "qs_tile")
      )
      Log.i(TAG, "broadcasted trigger")
    } catch (e: Exception) {
      Log.e(TAG, "broadcast failed", e)
    }

    // 2) Fallback path: use last cached screen text (in case AccessibilityService isn't running)
    // This makes QS tile feel responsive instead of "nothing happens".
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
        // Gentle hint (won't spam if user uses QS repeatedly and cache is empty)
        Toast.makeText(this, "未获取到页面文本：请确保已开启无障碍，并打开目标页面后再点“记一笔”", Toast.LENGTH_SHORT).show()
      }
    } catch (e: Exception) {
      Log.e(TAG, "fallback start service failed", e)
    }

    finish()
    overridePendingTransition(0, 0)
  }
}
