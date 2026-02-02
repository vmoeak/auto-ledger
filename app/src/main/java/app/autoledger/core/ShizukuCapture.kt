package app.autoledger.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import app.autoledger.overlay.OverlayConfirmService
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream

object ShizukuCapture {

  private const val TAG = "AutoLedger/Shizuku"

  fun isAvailable(): Boolean {
    return try {
      val binder = Shizuku.getBinder()
      val alive = binder?.isBinderAlive == true
      val ping = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
      Log.i(TAG, "isAvailable: binder=${binder != null} alive=$alive ping=$ping")
      alive || ping
    } catch (_: Throwable) {
      false
    }
  }

  fun hasPermission(): Boolean {
    return try {
      val granted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
      Log.i(TAG, "hasPermission: granted=$granted")
      granted
    } catch (_: Throwable) {
      false
    }
  }

  fun isEnabled(ctx: Context): Boolean {
    val cfg = AppConfig(ctx)
    return cfg.useShizukuCapture && isAvailable() && hasPermission()
  }

  /**
   * Capture a screenshot using Shizuku (adb/root) and start overlay in screenshot mode.
   * Returns true if capture was started.
   */
  fun captureToOverlay(ctx: Context, reason: String): Boolean {
    if (!isEnabled(ctx)) return false
    val appCtx = ctx.applicationContext
    Thread {
      try {
        Log.i(TAG, "captureToOverlay start reason=$reason")
        val bmp = captureOnce()
        if (bmp == null) {
          Log.e(TAG, "captureOnce returned null")
          return@Thread
        }
        ScreenshotStore.put(bmp)
        val i = Intent(appCtx, OverlayConfirmService::class.java)
        i.putExtra(Actions.EXTRA_TRIGGER_SOURCE, reason)
        i.putExtra(Actions.EXTRA_SCREENSHOT_MODE, true)
        val component = appCtx.startService(i)
        Log.i(TAG, "OverlayConfirmService started via Shizuku component=$component")
      } catch (e: Exception) {
        Log.e(TAG, "captureToOverlay failed", e)
      }
    }.start()
    return true
  }

  private fun captureOnce(): Bitmap? {
    var proc: Process? = null
    return try {
      proc = newProcess(arrayOf("screencap", "-p"))
      if (proc == null) {
        Log.e(TAG, "Shizuku.newProcess not available")
        return null
      }
      val outputBytes = proc.inputStream.use { input ->
        val buf = ByteArrayOutputStream()
        input.copyTo(buf)
        buf.toByteArray()
      }
      val errBytes = proc.errorStream.use { it.readBytes() }
      val exitCode = proc.waitFor()
      if (exitCode != 0) {
        val err = if (errBytes.isNotEmpty()) String(errBytes) else "unknown"
        Log.e(TAG, "screencap exit=$exitCode err=$err")
        return null
      }
      if (outputBytes.isEmpty()) {
        Log.e(TAG, "screencap returned empty output")
        return null
      }
      BitmapFactory.decodeByteArray(outputBytes, 0, outputBytes.size)
    } catch (e: Exception) {
      Log.e(TAG, "screencap failed", e)
      null
    } finally {
      try {
        proc?.destroy()
      } catch (_: Exception) {}
    }
  }

  private fun newProcess(cmd: Array<String>): Process? {
    return try {
      val method = Shizuku::class.java.getDeclaredMethod(
        "newProcess",
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java
      )
      method.isAccessible = true
      method.invoke(null, cmd, null, null) as? Process
    } catch (e: Exception) {
      Log.e(TAG, "reflect newProcess failed", e)
      null
    }
  }
}
