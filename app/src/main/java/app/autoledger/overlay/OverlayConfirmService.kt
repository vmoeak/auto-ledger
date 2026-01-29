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
      Toast.makeText(this, "当前页面未识别到可读文字（需要无障碍辅助读取）", Toast.LENGTH_SHORT).show()
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
    try {
      wm.addView(v, params)
    } catch (e: Exception) {
      Log.e(TAG, "wm.addView failed (overlay permission?)", e)
      Toast.makeText(this, "无法弹出悬浮窗：请在系统设置中开启“在其他应用上层显示/悬浮窗”权限", Toast.LENGTH_LONG).show()
      // Best effort: jump to overlay permission settings.
      try {
        val i = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
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
      toast("API Key 未设置")
      removeCard(); stopSelf(); return
    }
    val ledgerUri = cfg.ledgerUri
    if (ledgerUri == null) {
      toast("未选择账本文件（ledger.csv）")
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
          subtitle.text = "确认并保存"
          timeEt.setText(app.autoledger.core.LedgerWriter.normalizeTime(parsed.time_local ?: now))
          appEt.setText(parsed.app ?: "Unknown")
          amountEt.setText(app.autoledger.core.LedgerWriter.formatAmount(parsed.amount))
          currencyEt.setText(parsed.currency ?: "CNY")
          merchantEt.setText(parsed.merchant ?: "")
          noteEt.setText(parsed.note ?: "")
          confTv.text = "置信度：${parsed.confidence ?: ""}"
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
              toast("已保存")
            } catch (e: Exception) {
              Log.e(TAG, "save failed", e)
              toast("保存失败：${e.message}")
            }
            removeCard(); stopSelf()
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "parse failed", e)
        Handler(Looper.getMainLooper()).post {
          subtitle.text = "解析失败"
          toast("解析失败：${e.message}")
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
