package app.autoledger.accessibility

import android.content.Context

object ScreenTextCache {
  private const val PREF = "auto_ledger_cache"
  private const val KEY_TEXT = "lastText"
  private const val KEY_TS = "lastTs"

  fun put(ctx: Context, text: String) {
    ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_TEXT, text)
      .putLong(KEY_TS, System.currentTimeMillis())
      .apply()
  }

  fun get(ctx: Context): Pair<String, Long> {
    val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val text = sp.getString(KEY_TEXT, "") ?: ""
    val ts = sp.getLong(KEY_TS, 0L)
    return text to ts
  }
}
