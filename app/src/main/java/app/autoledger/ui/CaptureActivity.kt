package app.autoledger.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.autoledger.core.AppConfig
import app.autoledger.core.CapturePermissionStore
import app.autoledger.core.LedgerWriter
import app.autoledger.core.OpenAiCompatClient
import app.autoledger.databinding.DialogConfirmBinding
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureActivity : AppCompatActivity() {

  private lateinit var cfg: AppConfig

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    cfg = AppConfig(this)

    // Minimal checks
    if (cfg.apiKey.isBlank()) {
      toast("API key missing")
      finish()
      return
    }
    val ledgerUri = cfg.ledgerUri
    if (ledgerUri == null) {
      toast("ledger.csv missing (choose file in app)")
      finish()
      return
    }
    val resultData = CapturePermissionStore.resultData
    val resultCode = CapturePermissionStore.resultCode
    if (resultData == null || resultCode == null) {
      toast("Capture permission missing. Open app and tap 'Authorize screen capture'.")
      finish()
      return
    }

    captureOnce(resultCode, resultData)
  }

  private fun captureOnce(resultCode: Int, resultData: android.content.Intent) {
    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val projection: MediaProjection = mgr.getMediaProjection(resultCode, resultData)

    val metrics = resources.displayMetrics
    val width = metrics.widthPixels
    val height = metrics.heightPixels
    val dpi = metrics.densityDpi

    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    var vDisplay: VirtualDisplay? = null

    val handler = Handler(Looper.getMainLooper())

    reader.setOnImageAvailableListener({ r ->
      val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
      val planes = image.planes
      val buffer: ByteBuffer = planes[0].buffer
      val pixelStride = planes[0].pixelStride
      val rowStride = planes[0].rowStride
      val rowPadding = rowStride - pixelStride * width

      val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
      bmp.copyPixelsFromBuffer(buffer)
      image.close()

      // Crop to screen size
      val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)

      // Cleanup
      try { reader.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
      vDisplay?.release()
      projection.stop()

      // Process
      handler.post {
        parseAndConfirm(cropped)
      }
    }, handler)

    vDisplay = projection.createVirtualDisplay(
      "auto-ledger",
      width,
      height,
      dpi,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      reader.surface,
      null,
      handler
    )

    // Wait a moment so the page settles.
    handler.postDelayed({
      // A second chance if no image delivered; ImageReader will call listener anyway.
    }, 500)
  }

  private fun parseAndConfirm(bitmap: Bitmap) {
    val ledgerUri = cfg.ledgerUri ?: run { finish(); return }

    val client = OpenAiCompatClient(cfg.baseUrl, cfg.apiKey, cfg.model)

    Thread {
      try {
        val parsed = client.parseExpenseFromScreenshot(bitmap)

        runOnUiThread {
          val binding = DialogConfirmBinding.inflate(LayoutInflater.from(this))
          val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

          binding.timeLocal.setText(parsed.time_local ?: now)
          binding.app.setText(parsed.app ?: "Unknown")
          binding.amount.setText(parsed.amount?.toString() ?: "")
          binding.currency.setText(parsed.currency ?: "CNY")
          binding.merchant.setText(parsed.merchant ?: "")
          binding.note.setText(parsed.note ?: "")
          binding.confidence.text = "confidence: ${parsed.confidence ?: ""}"

          AlertDialog.Builder(this)
            .setTitle("Confirm record")
            .setView(binding.root)
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setPositiveButton("Save") { _, _ ->
              try {
                LedgerWriter.ensureHeader(this, ledgerUri)

                val time = binding.timeLocal.text?.toString() ?: now
                val app = binding.app.text?.toString() ?: "Unknown"
                val amount = binding.amount.text?.toString() ?: ""
                val currency = binding.currency.text?.toString() ?: "CNY"
                val merchant = binding.merchant.text?.toString() ?: ""
                val note = binding.note.text?.toString() ?: ""
                val conf = parsed.confidence?.toString() ?: ""
                val raw = parsed.raw ?: ""

                val row = listOf(
                  time,
                  app,
                  amount,
                  currency,
                  merchant,
                  "", // category
                  note,
                  conf,
                  LedgerWriter.csvEscape(raw)
                ).joinToString(",")

                LedgerWriter.appendRow(this, ledgerUri, row)
                toast("Saved")
              } catch (e: Exception) {
                toast("Save failed: ${e.message}")
              }
              finish()
            }
            .setOnCancelListener { finish() }
            .show()
        }
      } catch (e: Exception) {
        runOnUiThread {
          toast("Parse failed: ${e.message}")
          finish()
        }
      }
    }.start()
  }

  private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
