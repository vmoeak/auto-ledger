package app.autoledger.core

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AppConfig(context: Context) {
  private val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

  private val prefs = EncryptedSharedPreferences.create(
    context,
    "auto_ledger_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
  )

  var baseUrl: String
    get() = prefs.getString("baseUrl", "https://api.openai.com") ?: "https://api.openai.com"
    set(v) { prefs.edit().putString("baseUrl", v.trimEnd('/')).apply() }

  var apiKey: String
    get() = prefs.getString("apiKey", "") ?: ""
    set(v) { prefs.edit().putString("apiKey", v).apply() }

  var model: String
    get() = prefs.getString("model", "gpt-4.1-mini") ?: "gpt-4.1-mini"
    set(v) { prefs.edit().putString("model", v).apply() }

  var ledgerUri: Uri?
    get() {
      val s = prefs.getString("ledgerUri", null) ?: return null
      return try { Uri.parse(s) } catch (_: Exception) { null }
    }
    set(v) {
      prefs.edit().putString("ledgerUri", v?.toString()).apply()
    }

  var hasCapturePermissionInMemory: Boolean
    get() = prefs.getBoolean("dummy", false)
    set(_) {}
}
