package app.autoledger.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import app.autoledger.accessibility.ScreenTextCache
import app.autoledger.core.Actions
import app.autoledger.overlay.OverlayConfirmService

class CaptureTileService : TileService() {
  private val TAG = "AutoLedger/Tile"

  override fun onStartListening() {
    super.onStartListening()
    Log.i(TAG, "onStartListening")
    try {
      qsTile?.state = Tile.STATE_ACTIVE
      qsTile?.updateTile()
    } catch (_: Exception) {}
  }

  override fun onClick() {
    super.onClick()
    Log.i(TAG, "QS tile clicked")

    // Prefer cached text (updated continuously by Accessibility events).
    val (cached, ts) = ScreenTextCache.get(this)
    val ageMs = System.currentTimeMillis() - ts
    Log.i(TAG, "cachedText len=${cached.length} ageMs=$ageMs")

    if (cached.isNotBlank() && ageMs < 15_000) {
      // Directly show overlay confirm without any app switch.
      val i = Intent(this, OverlayConfirmService::class.java)
        .putExtra(Actions.EXTRA_TRIGGER_SOURCE, "qs_tile")
        .putExtra(Actions.EXTRA_EXTRACTED_TEXT, cached)
      try {
        startService(i)
      } catch (e: Throwable) {
        Log.e(TAG, "startService OverlayConfirmService failed", e)
      }
    } else {
      // Fallback: ask AccessibilityService to extract right now.
      try {
        sendBroadcast(Intent(Actions.ACTION_TRIGGER_LEDGER)
          .setPackage(packageName)
          .putExtra(Actions.EXTRA_TRIGGER_SOURCE, "qs_tile"))
      } catch (e: Throwable) {
        Log.e(TAG, "broadcast trigger failed", e)
      }
    }

    // Best-effort tile refresh.
    try {
      qsTile?.let {
        it.state = Tile.STATE_ACTIVE
        it.updateTile()
      }
    } catch (_: Throwable) {}
  }
}
