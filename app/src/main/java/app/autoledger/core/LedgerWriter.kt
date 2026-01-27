package app.autoledger.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.OutputStream

object LedgerWriter {

  // Add UTF-8 BOM so Excel/WPS reliably recognizes UTF-8.
  private val header = "\uFEFFtime,app,amount,currency,merchant,category,note,confidence,raw\n"

  fun ensureHeader(context: Context, uri: Uri) {
    val resolver = context.contentResolver
    val size = querySize(resolver, uri)
    if (size == null || size == 0L) {
      resolver.openOutputStream(uri, "wa")?.use {
        it.write(header.toByteArray())
        it.flush()
      }
    }
  }

  fun appendRow(context: Context, uri: Uri, row: String) {
    val resolver = context.contentResolver
    resolver.openOutputStream(uri, "wa")?.use {
      it.write((row + "\n").toByteArray())
      it.flush()
    } ?: throw RuntimeException("Cannot open ledger uri")
  }

  private fun querySize(resolver: ContentResolver, uri: Uri): Long? {
    return try {
      resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getLong(0) else null
      }
    } catch (_: Exception) {
      null
    }
  }

  fun csvEscape(s: String?): String {
    if (s == null) return "\"\""
    val escaped = s.replace("\"", "\"\"")
    return "\"$escaped\""
  }

  /**
   * Make timestamps shorter for spreadsheet display.
   * Converts `yyyy-MM-dd HH:mm:ss` -> `yyyy-MM-dd HH:mm`.
   */
  fun normalizeTime(s: String): String {
    val t = s.trim()
    return if (t.length == 19 && t[4] == '-' && t[7] == '-' && t[10] == ' ' && t[13] == ':' && t[16] == ':') {
      t.substring(0, 16)
    } else t
  }

  /**
   * Prevent huge cells that ruin mobile spreadsheet UX.
   */
  fun compactRaw(raw: String?, maxChars: Int = 240): String {
    if (raw.isNullOrBlank()) return ""
    val r = raw.trim()
    return if (r.length > maxChars) r.substring(0, maxChars) + "…" else r
  }

  fun formatAmount(n: Double?): String {
    if (n == null) return ""
    val asLong = n.toLong()
    return if (n == asLong.toDouble()) asLong.toString() else n.toString()
  }
}
