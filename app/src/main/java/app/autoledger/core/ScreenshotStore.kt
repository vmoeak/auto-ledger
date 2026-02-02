package app.autoledger.core

import android.graphics.Bitmap

/**
 * In-memory singleton to pass a screenshot bitmap from AccessibilityService
 * to OverlayConfirmService without exceeding the 1 MB Binder transaction limit.
 */
object ScreenshotStore {
  @Volatile
  private var bitmap: Bitmap? = null

  fun put(bmp: Bitmap) {
    bitmap?.recycle()
    bitmap = bmp
  }

  /** Returns the stored bitmap and clears the reference. Caller owns the bitmap. */
  fun take(): Bitmap? {
    val bmp = bitmap
    bitmap = null
    return bmp
  }
}
