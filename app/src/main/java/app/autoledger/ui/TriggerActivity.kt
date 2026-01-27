package app.autoledger.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import app.autoledger.core.Actions

/**
 * A no-UI trampoline activity used to collapse QS panel reliably on newer Android.
 * It immediately broadcasts a trigger and finishes without animation.
 */
class TriggerActivity : Activity() {
  private val TAG = "AutoLedger/TriggerActivity"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      sendBroadcast(Intent(Actions.ACTION_TRIGGER_LEDGER).setPackage(packageName)
        .putExtra(Actions.EXTRA_TRIGGER_SOURCE, "qs_tile"))
      Log.i(TAG, "broadcasted trigger")
    } catch (e: Exception) {
      Log.e(TAG, "broadcast failed", e)
    }
    finish()
    overridePendingTransition(0, 0)
  }
}
