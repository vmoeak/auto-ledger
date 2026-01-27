package app.autoledger.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import app.autoledger.R
import app.autoledger.core.Actions
import app.autoledger.core.AppConfig
import app.autoledger.core.LedgerWriter
import app.autoledger.core.OpenAiCompatClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OverlayConfirmService : Service() {

  private val TAG = "AutoLedger/OverlayConfirm"

  private lateinit var wm: WindowManager
  private var rootView: android.view.View? = null

  override fun onCreate() {
    super.onCreate()
    wm = getSystemService(WINDOW_SERVICE) as WindowManager
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val extractedText = intent?.getStringExtra(Actions.EXTRA_EXTRACTED_TEXT).orEmpty()
    val triggerSource = intent?.getStringExtra(Actions.EXTRA_TRIGGER_SOURCE) ?: "(unknown)"
    Log.i(TAG, "onStartCommand src=$triggerSource extractedLen=${extractedText.length}")

    if (extractedText.isBlank()) {
      Toast.makeText(this, "No readable text (Accessibility)", Toast.LENGTH_SHORT).show()
      stopSelf()
      return START_NOT_STICKY
    }

    showOrReplaceCard()
    parseAndBind(extractedText)

    return START_NOT_STICKY
  }

  private fun showOrReplaceCard() {
    removeCard()
    val v = LayoutInflater.from(this).inflate(R.layout.overlay_confirm_card, null)

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT
    )
    params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
    params.x = 24
    params.y = 0

    // Buttons
    v.findViewById<Button>(R.id.cancelBtn).setOnClickListener {
      removeCard()
      stopSelf()
    }

    // disable save until parsed
    v.findViewById<Button>(R.id.saveBtn).isEnabled = false

    rootView = v
    wm.addView(v, params)
  }

  private fun parseAndBind(extractedText: String) {
    val cfg = AppConfig(this)
    if (cfg.apiKey.isBlank()) {
      toast("API key missing")
      removeCard(); stopSelf(); return
    }
    val ledgerUri = cfg.ledgerUri
    if (ledgerUri == null) {
      toast("ledger.csv missing")
      removeCard(); stopSelf(); return
    }

    val v = rootView ?: return
    val subtitle = v.findViewById<TextView>(R.id.subtitle)
    val saveBtn = v.findViewById<Button>(R.id.saveBtn)

    val timeEt = v.findViewById<EditText>(R.id.timeLocal)
    val appEt = v.findViewById<EditText>(R.id.app)
    val amountEt = v.findViewById<EditText>(R.id.amount)
    val currencyEt = v.findViewById<EditText>(R.id.currency)
    val merchantEt = v.findViewById<EditText>(R.id.merchant)
    val noteEt = v.findViewById<EditText>(R.id.note)
    val confTv = v.findViewById<TextView>(R.id.confidence)

    val client = OpenAiCompatClient(cfg.baseUrl, cfg.apiKey, cfg.model)

    Thread {
      try {
        Log.i(TAG, "calling model")
        val parsed = client.parseExpenseFromText(extractedText)
        Log.i(TAG, "parse ok conf=${parsed.confidence}")

        Handler(Looper.getMainLooper()).post {
          val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
          subtitle.text = "Review & save"
          timeEt.setText(parsed.time_local ?: now)
          appEt.setText(parsed.app ?: "Unknown")
          amountEt.setText(parsed.amount?.toString() ?: "")
          currencyEt.setText(parsed.currency ?: "CNY")
          merchantEt.setText(parsed.merchant ?: "")
          noteEt.setText(parsed.note ?: "")
          confTv.text = "confidence: ${parsed.confidence ?: ""}"
          saveBtn.isEnabled = true

          saveBtn.setOnClickListener {
            try {
              LedgerWriter.ensureHeader(this, ledgerUri)
              val row = listOf(
                (timeEt.text?.toString() ?: now),
                (appEt.text?.toString() ?: "Unknown"),
                (amountEt.text?.toString() ?: ""),
                (currencyEt.text?.toString() ?: "CNY"),
                (merchantEt.text?.toString() ?: ""),
                "", // category
                (noteEt.text?.toString() ?: ""),
                (parsed.confidence?.toString() ?: ""),
                LedgerWriter.csvEscape(parsed.raw ?: "")
              ).joinToString(",")
              LedgerWriter.appendRow(this, ledgerUri, row)
              toast("Saved")
            } catch (e: Exception) {
              Log.e(TAG, "save failed", e)
              toast("Save failed: ${e.message}")
            }
            removeCard(); stopSelf()
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "parse failed", e)
        Handler(Looper.getMainLooper()).post {
          subtitle.text = "Parse failed"
          toast("Parse failed: ${e.message}")
          removeCard(); stopSelf()
        }
      }
    }.start()
  }

  private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

  private fun removeCard() {
    try {
      rootView?.let { wm.removeView(it) }
    } catch (_: Exception) {}
    rootView = null
  }

  override fun onDestroy() {
    removeCard()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
