package app.autoledger.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import app.autoledger.core.AppConfig
import app.autoledger.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

  private val TAG = "AutoLedger/Main"

  private lateinit var b: ActivityMainBinding
  private lateinit var cfg: AppConfig

  /**
   * Pick an existing ledger.csv.
   *
   * NOTE: CreateDocument can only create a new file, it cannot select an existing one.
   * We use ACTION_OPEN_DOCUMENT so users can pick an existing ledger.csv.
   */
  private val pickLedger = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
    if (res.resultCode == Activity.RESULT_OK) {
      val uri: Uri? = res.data?.data
      if (uri != null) {
        // Persist permissions so the app can read/write the file later.
        val flags = (res.data?.flags ?: 0) and
          (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
          contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
          // Some providers may not grant write permission; still store uri.
          Log.w(TAG, "takePersistableUriPermission failed", e)
        }
        cfg.ledgerUri = uri
        toast("已选择账本文件")
        updateStatus()
      }
    }
  }

  // 截图/悬浮入口已从主界面移除（仍可通过快捷设置磁贴等方式使用）。

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityMainBinding.inflate(layoutInflater)
    setContentView(b.root)

    cfg = AppConfig(this)

    b.baseUrl.setText(cfg.baseUrl)
    b.apiKey.setText(cfg.apiKey)
    b.model.setText(cfg.model)

    b.saveConfig.setOnClickListener {
      cfg.baseUrl = b.baseUrl.text?.toString() ?: ""
      cfg.apiKey = b.apiKey.text?.toString() ?: ""
      cfg.model = b.model.text?.toString() ?: ""
      Log.i(TAG, "Saved config baseUrl=${cfg.baseUrl} model=${cfg.model} apiKeySet=${cfg.apiKey.isNotBlank()}")
      toast("已保存")
      updateStatus()
    }

    b.pickLedger.setOnClickListener {
      val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "text/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "application/vnd.ms-excel"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
      }
      pickLedger.launch(intent)
    }

    // 主界面已移除截图/悬浮按钮相关入口。

    b.viewStats.setOnClickListener {
      Log.i(TAG, "View Stats clicked")
      startActivity(Intent(this, StatsActivity::class.java))
    }

    updateStatus()
  }

  private fun updateStatus() {
    val ledgerOk = cfg.ledgerUri != null
    val keyOk = cfg.apiKey.isNotBlank()
    b.status.text = "状态：\n" +
      "- Base URL：${cfg.baseUrl}\n" +
      "- 模型：${cfg.model}\n" +
      "- API Key：${if (keyOk) "已设置" else "未设置"}\n" +
      "- 账本文件：${if (ledgerOk) "已选择" else "未选择"}\n"
  }

  private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
