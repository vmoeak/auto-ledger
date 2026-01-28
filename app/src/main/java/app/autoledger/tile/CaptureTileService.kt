package app.autoledger.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import app.autoledger.core.Actions
import app.autoledger.ui.TriggerActivity

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

    // Use startActivityAndCollapse to reliably collapse the QS panel,
    // then TriggerActivity broadcasts to AccessibilityService.
    try {
      val intent = Intent(this, TriggerActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(Actions.EXTRA_TRIGGER_SOURCE, "qs_tile")
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // API 34+: must use PendingIntent
        val pi = PendingIntent.getActivity(
          this, 0, intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pi)
      } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
      }
    } catch (e: Throwable) {
      Log.e(TAG, "startActivityAndCollapse failed", e)
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
