package app.autoledger.tile

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
    postDebugNotification("QS tile clicked")

    val i = Intent(this, CaptureActivity::class.java)
      .putExtra("triggerSource", "qs_tile")
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // Newer Android versions are more reliable with PendingIntent.
    val pi = PendingIntent.getActivity(
      this,
      0,
      i,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
      startActivityAndCollapse(pi)
    } catch (e: Throwable) {
      Log.e(TAG, "startActivityAndCollapse(PendingIntent) failed", e)
      // Fallback
      try { startActivity(i) } catch (e2: Throwable) {
        Log.e(TAG, "startActivity fallback failed", e2)
      }
    }
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
