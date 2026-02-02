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
import android.provider.Settings
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
      Log.w(TAG, "extractedText is blank, showing toast and stopping")
      Toast.makeText(this, "\u5f53\u524d\u9875\u9762\u672a\u8bc6\u522b\u5230\u53ef\u8bfb\u6587\u5b57\uff08\u9700\u8981\u65e0\u969c\u788d\u8f85\u52a9\u8bfb\u53d6\uff09", Toast.LENGTH_SHORT).show()
      stopSelf()
      return START_NOT_STICKY
    }

    showOrReplaceCard()
    parseAndBind(extractedText)

    return START_NOT_STICKY
  }

  private fun showOrReplaceCard() {
    removeCard()

    val canDraw = Settings.canDrawOverlays(this)
    Log.i(TAG, "showOrReplaceCard: canDrawOverlays=$canDraw")
    if (!canDraw) {
      Log.e(TAG, "Overlay permission NOT granted - cannot show card")
      Toast.makeText(this, "\u65e0\u6cd5\u5f39\u51fa\u60ac\u6d6e\u7a97\uff1a\u8bf7\u5728\u7cfb\u7edf\u8bbe\u7f6e\u4e2d\u5f00\u542f\u201c\u5728\u5176\u4ed6\u5e94\u7528\u4e0a\u5c42\u663e\u793a/\u60ac\u6d6e\u7a97\u201d\u6743\u9650", Toast.LENGTH_LONG).show()
      try {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
          data = android.net.Uri.parse("package:$packageName")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(i)
      } catch (_: Exception) {}
      stopSelf()
      return
    }

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

    Log.i(TAG, "addView: type=${params.type} flags=0x${Integer.toHexString(params.flags)} gravity=${params.gravity}")

    // Buttons
    v.findViewById<Button>(R.id.cancelBtn).setOnClickListener {
      removeCard()
      stopSelf()
    }

    // disable save until parsed
    v.findViewById<Button>(R.id.saveBtn).isEnabled = false

    rootView = v
    try {
      wm.addView(v, params)
      Log.i(TAG, "wm.addView succeeded - overlay card should be visible")
    } catch (e: Exception) {
      Log.e(TAG, "wm.addView FAILED (exception class=${e.javaClass.simpleName})", e)
      Toast.makeText(this, "\u65e0\u6cd5\u5f39\u51fa\u60ac\u6d6e\u7a97\uff1a\u8bf7\u5728\u7cfb\u7edf\u8bbe\u7f6e\u4e2d\u5f00\u542f\u201c\u5728\u5176\u4ed6\u5e94\u7528\u4e0a\u5c42\u663e\u793a/\u60ac\u6d6e\u7a97\u201d\u6743\u9650", Toast.LENGTH_LONG).show()
      try {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
          data = android.net.Uri.parse("package:$packageName")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(i)
      } catch (_: Exception) {}
      stopSelf()
    }
  }

  private fun parseAndBind(extractedText: String) {
    val cfg = AppConfig(this)
    if (cfg.apiKey.isBlank()) {
      toast("API Key \u672a\u8bbe\u7f6e")
      removeCard(); stopSelf(); return
    }
    val ledgerUri = cfg.ledgerUri
    if (ledgerUri == null) {
      toast("\u672a\u9009\u62e9\u8d26\u672c\u6587\u4ef6\uff08ledger.csv\uff09")
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
          subtitle.text = "\u786e\u8ba4\u5e76\u4fdd\u5b58"
          timeEt.setText(app.autoledger.core.LedgerWriter.normalizeTime(parsed.time_local ?: now))
          appEt.setText(parsed.app ?: "Unknown")
          amountEt.setText(app.autoledger.core.LedgerWriter.formatAmount(parsed.amount))
          currencyEt.setText(parsed.currency ?: "CNY")
          merchantEt.setText(parsed.merchant ?: "")
          noteEt.setText(parsed.note ?: "")
          confTv.text = "\u7f6e\u4fe1\u5ea6\uff1a${parsed.confidence ?: ""}"
          saveBtn.isEnabled = true

          saveBtn.setOnClickListener {
            try {
              LedgerWriter.ensureHeader(this, ledgerUri)
              val row = listOf(
                LedgerWriter.normalizeTime(timeEt.text?.toString() ?: now),
                (appEt.text?.toString() ?: "Unknown"),
                (amountEt.text?.toString() ?: ""),
                (currencyEt.text?.toString() ?: "CNY"),
                (merchantEt.text?.toString() ?: ""),
                "", // category
                (noteEt.text?.toString() ?: ""),
                (parsed.confidence?.toString() ?: ""),
                LedgerWriter.csvEscape(LedgerWriter.compactRaw(parsed.raw))
              ).joinToString(",")
              LedgerWriter.appendRow(this, ledgerUri, row)
              toast("\u5df2\u4fdd\u5b58")
            } catch (e: Exception) {
              Log.e(TAG, "save failed", e)
              toast("\u4fdd\u5b58\u5931\u8d25\uff1a${e.message}")
            }
            removeCard(); stopSelf()
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "parse failed", e)
        Handler(Looper.getMainLooper()).post {
          subtitle.text = "\u89e3\u6790\u5931\u8d25"
          toast("\u89e3\u6790\u5931\u8d25\uff1a${e.message}")
          // Keep the card briefly so users can see something happened even if toast is suppressed.
          Handler(Looper.getMainLooper()).postDelayed({
            removeCard(); stopSelf()
          }, 2500)
        }
      }
    }.start()
  }

  private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

  private fun removeCard() {
    try {
      rootView?.let {
        wm.removeView(it)
        Log.d(TAG, "removeCard: overlay removed")
      }
    } catch (e: Exception) {
      Log.w(TAG, "removeCard failed", e)
    }
    rootView = null
  }

  override fun onDestroy() {
    Log.d(TAG, "onDestroy")
    removeCard()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
