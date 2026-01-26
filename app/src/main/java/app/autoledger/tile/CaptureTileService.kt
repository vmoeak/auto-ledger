package app.autoledger.tile

import android.content.Intent
import android.service.quicksettings.TileService
import app.autoledger.ui.CaptureActivity

class CaptureTileService : TileService() {
  override fun onClick() {
    super.onClick()
    val i = Intent(this, CaptureActivity::class.java)
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivityAndCollapse(i)
  }
}
