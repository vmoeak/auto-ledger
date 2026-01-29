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
   * Normalize timestamps for spreadsheet + downstream parsing.
   *
   * Goals:
   * - Keep output stable: `yyyy-MM-dd HH:mm` (or `yyyy-MM-dd` if no time)
   * - Accept common variants from LLM / copy-paste:
   *   - `yyyy-MM-dd HH:mm:ss`
   *   - `yyyy/MM/dd HH:mm` / `yyyy/M/d H:mm[:ss]`
   *   - `yyyy年M月d日 HH:mm[:ss]`
   */
  fun normalizeTime(s: String): String {
    val t = s.trim()
    if (t.isBlank()) return ""

    // Fast path: already `yyyy-MM-dd HH:mm`
    if (t.length == 16 && t[4] == '-' && t[7] == '-' && t[10] == ' ' && t[13] == ':') return t

    // Fast path: `yyyy-MM-dd HH:mm:ss` -> `yyyy-MM-dd HH:mm`
    if (t.length == 19 && t[4] == '-' && t[7] == '-' && t[10] == ' ' && t[13] == ':' && t[16] == ':') {
      return t.substring(0, 16)
    }

    // Robust parse for slash/Chinese formats.
    return try {
      val dt = parseLocalDateTimeLoose(t)
      dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } catch (_: Exception) {
      try {
        val d = parseLocalDateLoose(t)
        d.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
      } catch (_: Exception) {
        // Fall back to original string if we can't parse.
        t
      }
    }
  }

  private fun parseLocalDateTimeLoose(t: String): java.time.LocalDateTime {
    val candidates = listOf(
      "yyyy/M/d H:mm:ss",
      "yyyy/M/d HH:mm:ss",
      "yyyy/M/d H:mm",
      "yyyy/M/d HH:mm",
      "yyyy/MM/dd HH:mm:ss",
      "yyyy/MM/dd HH:mm",
      "yyyy年M月d日 H:mm:ss",
      "yyyy年M月d日 HH:mm:ss",
      "yyyy年M月d日 H:mm",
      "yyyy年M月d日 HH:mm",
      "yyyy-MM-dd H:mm:ss",
      "yyyy-MM-dd HH:mm:ss",
      "yyyy-MM-dd H:mm",
      "yyyy-MM-dd HH:mm",
    )
    for (p in candidates) {
      try {
        val f = java.time.format.DateTimeFormatter.ofPattern(p)
        return java.time.LocalDateTime.parse(t, f)
      } catch (_: Exception) {
        // try next
      }
    }
    throw IllegalArgumentException("Unparseable datetime: $t")
  }

  private fun parseLocalDateLoose(t: String): java.time.LocalDate {
    val candidates = listOf(
      "yyyy/M/d",
      "yyyy/MM/dd",
      "yyyy年M月d日",
      "yyyy-MM-dd",
    )
    for (p in candidates) {
      try {
        val f = java.time.format.DateTimeFormatter.ofPattern(p)
        return java.time.LocalDate.parse(t, f)
      } catch (_: Exception) {
        // try next
      }
    }
    throw IllegalArgumentException("Unparseable date: $t")
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
