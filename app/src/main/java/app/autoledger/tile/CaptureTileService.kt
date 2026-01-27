package app.autoledger.tile

import android.content.Intent
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import app.autoledger.ui.CaptureActivity

class CaptureTileService : TileService() {
  private val TAG = "AutoLedger/Tile"

  override fun onClick() {
    super.onClick()
    Log.i(TAG, "QS tile clicked")
    Toast.makeText(this, "Auto Ledger: triggered", Toast.LENGTH_SHORT).show()

    val i = Intent(this, CaptureActivity::class.java)
    i.putExtra("triggerSource", "qs_tile")
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivityAndCollapse(i)
  }
}
