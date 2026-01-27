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

    // Requirement: collapse control center FIRST, then read the current screen.
    // We do this by launching a transparent trampoline activity via PendingIntent.
    // TriggerActivity will broadcast ACTION_TRIGGER_LEDGER and immediately finish.
    try {
      val pi = android.app.PendingIntent.getActivity(
        this,
        0,
        Intent(this, app.autoledger.ui.TriggerActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
      )
      startActivityAndCollapse(pi)
    } catch (e: Throwable) {
      Log.e(TAG, "startActivityAndCollapse failed", e)
      // Fallback: if collapse fails, still try to trigger.
      try {
        sendBroadcast(Intent(Actions.ACTION_TRIGGER_LEDGER)
          .setPackage(packageName)
          .putExtra(Actions.EXTRA_TRIGGER_SOURCE, "qs_tile"))
      } catch (e2: Throwable) {
        Log.e(TAG, "broadcast trigger failed", e2)
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
