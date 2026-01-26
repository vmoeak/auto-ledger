package app.autoledger.core

import android.content.Intent

/**
 * MediaProjection permission Intent cannot be persisted reliably across process death.
 * Keep it in memory only (MVP).
 */
object CapturePermissionStore {
  var resultData: Intent? = null
  var resultCode: Int? = null
}
