package app.autoledger.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import app.autoledger.R

/**
 * Android 14+ (and newer, incl. Android 16) requires MediaProjection to be started
 * while a foreground service of type "mediaProjection" is running.
 */
class CaptureForegroundService : Service() {

  private val TAG = "AutoLedger/FGS"

  override fun onCreate() {
    super.onCreate()
    Log.i(TAG, "onCreate")

    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Auto Ledger capture",
        NotificationManager.IMPORTANCE_LOW
      )
      nm.createNotificationChannel(channel)
    }

    val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_camera)
      .setContentTitle("自动记账")
      .setContentText("Capturing screen for expense extraction…")
      .setOngoing(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .build()

    // Important: on Android 10+ you should specify the foreground-service type at runtime.
    // Android 14+/15+/16 may enforce this for MediaProjection.
    if (Build.VERSION.SDK_INT >= 29) {
      startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    } else {
      startForeground(NOTIF_ID, notif)
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.i(TAG, "onStartCommand")
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    Log.i(TAG, "onDestroy")
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    private const val CHANNEL_ID = "auto_ledger_capture"
    private const val NOTIF_ID = 1001

    fun start(ctx: Context) {
      val i = Intent(ctx, CaptureForegroundService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ctx.startForegroundService(i)
      } else {
        ctx.startService(i)
      }
    }

    fun stop(ctx: Context) {
      ctx.stopService(Intent(ctx, CaptureForegroundService::class.java))
    }
  }
}
