package app.autoledger.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.OutputStream

object LedgerWriter {

  private val header = "time,app,amount,currency,merchant,category,note,confidence,raw\n"

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
}
