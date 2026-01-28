package app.autoledger.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import app.autoledger.core.AppConfig
import app.autoledger.core.CapturePermissionStore
import app.autoledger.databinding.ActivityMainBinding
import app.autoledger.overlay.OverlayService

class MainActivity : AppCompatActivity() {

  private val TAG = "AutoLedger/Main"

  private lateinit var b: ActivityMainBinding
  private lateinit var cfg: AppConfig

  private val pickLedger = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
    if (uri != null) {
      contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
      cfg.ledgerUri = uri
      toast("Ledger set")
      updateStatus()
    }
  }

  private val requestCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
    if (res.resultCode == Activity.RESULT_OK && res.data != null) {
      CapturePermissionStore.resultCode = res.resultCode
      CapturePermissionStore.resultData = res.data
      toast("Capture authorized (in-memory)")
      updateStatus()
    } else {
      toast("Capture authorization canceled")
    }
  }

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
      toast("Saved")
      updateStatus()
    }

    b.pickLedger.setOnClickListener {
      pickLedger.launch("ledger.csv")
    }

    b.requestCapturePerm.setOnClickListener {
      val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      requestCapture.launch(mgr.createScreenCaptureIntent())
    }

    b.toggleOverlay.setOnClickListener {
      if (!Settings.canDrawOverlays(this)) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
        toast("Grant overlay permission then tap again")
        return@setOnClickListener
      }
      val running = OverlayService.isRunning
      val i = Intent(this, OverlayService::class.java)
      if (running) stopService(i) else startService(i)
      updateStatus()
    }

    b.testCapture.setOnClickListener {
      Log.i(TAG, "Test Capture clicked")
      startActivity(Intent(this, CaptureActivity::class.java))
    }

    b.viewStats.setOnClickListener {
      Log.i(TAG, "View Stats clicked")
      startActivity(Intent(this, StatsActivity::class.java))
    }

    updateStatus()
  }

  private fun updateStatus() {
    val ledgerOk = cfg.ledgerUri != null
    val captureOk = CapturePermissionStore.resultData != null
    val keyOk = cfg.apiKey.isNotBlank()
    b.status.text = "Status:\n" +
      "- baseUrl: ${cfg.baseUrl}\n" +
      "- model: ${cfg.model}\n" +
      "- apiKey: ${if (keyOk) "set" else "missing"}\n" +
      "- ledger.csv: ${if (ledgerOk) "set" else "missing"}\n" +
      "- capture permission: ${if (captureOk) "authorized" else "missing"}\n" +
      "- overlay running: ${OverlayService.isRunning}"
  }

  private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
