package app.autoledger.tile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import app.autoledger.ui.CaptureActivity

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

    // Trigger Accessibility flow via broadcast (so we can read the current screen text).
    // NOTE: We intentionally do NOT start any Activity here. Some devices show a visible "app jump".
    try {
      sendBroadcast(Intent(app.autoledger.core.Actions.ACTION_TRIGGER_LEDGER)
        .setPackage(packageName)
        .putExtra(app.autoledger.core.Actions.EXTRA_TRIGGER_SOURCE, "qs_tile"))
    } catch (e: Throwable) {
      Log.e(TAG, "broadcast trigger failed", e)
    }

    // Best-effort collapse without launching UI (may be ignored by system).
    try {
      this@CaptureTileService.qsTile?.let {
        it.state = Tile.STATE_ACTIVE
        it.updateTile()
      }
    } catch (_: Throwable) {}
  }

  private fun postDebugNotification(msg: String) {
    try {
      val nm = getSystemService(NotificationManager::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(
          NotificationChannel(CHANNEL_ID, "Auto Ledger", NotificationManager.IMPORTANCE_LOW)
        )
      }
      val notif = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle("Auto Ledger")
        .setContentText(msg)
        .setAutoCancel(true)
        .build()
      nm.notify(2002, notif)
    } catch (e: Throwable) {
      Log.e(TAG, "postDebugNotification failed", e)
    }
  }

  companion object {
    private const val CHANNEL_ID = "auto_ledger_debug"
  }
}
