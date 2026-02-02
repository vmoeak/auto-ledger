package app.autoledger.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import app.autoledger.core.Actions
import app.autoledger.core.CapturePermissionStore

class ProjectionPermissionActivity : Activity() {

  private val TAG = "AutoLedger/ProjectionPermission"

  private val requestProjection = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == RESULT_OK && result.data != null) {
      CapturePermissionStore.resultCode = result.resultCode
      CapturePermissionStore.resultData = result.data
      Log.i(TAG, "MediaProjection permission granted")
      startCaptureAfterPermission()
    } else {
      Log.w(TAG, "MediaProjection permission denied resultCode=${result.resultCode}")
      Toast.makeText(this, "屏幕录制授权失败", Toast.LENGTH_SHORT).show()
      finish()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val intent = mgr.createScreenCaptureIntent()
    Log.i(TAG, "requesting MediaProjection permission")
    requestProjection.launch(intent)
  }

  private fun startCaptureAfterPermission() {
    val reason = intent?.getStringExtra(Actions.EXTRA_TRIGGER_SOURCE) ?: "(unknown)"
    val captureIntent = Intent(this, CaptureActivity::class.java)
      .putExtra(Actions.EXTRA_TRIGGER_SOURCE, reason)
    try {
      startActivity(captureIntent)
    } catch (e: Exception) {
      Log.e(TAG, "start CaptureActivity failed", e)
      Toast.makeText(this, "启动截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
    } finally {
      finish()
    }
  }
}
