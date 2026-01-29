package app.autoledger.core

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

data class LedgerEntry(
  val time: String,
  val app: String,
  val amount: Double,
  val currency: String,
  val merchant: String,
  val category: String,
  val note: String,
  val confidence: Double?,
  val raw: String
)

object LedgerReader {

  /**
   * Read ledger.csv reliably.
   *
   * IMPORTANT: CSV fields (especially `raw`) often contain embedded newlines.
   * The previous implementation used `forEachLine`, which breaks multi-line
   * quoted fields and caused "No data found".
   */
  fun readAll(context: Context, uri: Uri): List<LedgerEntry> {
    val entries = mutableListOf<LedgerEntry>()
    try {
      context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
          val rows = parseCsv(reader)
          if (rows.isEmpty()) return entries

          // Header (handle BOM on first cell)
          val header = rows.first().mapIndexed { idx, v -> if (idx == 0) v.trim().removePrefix("\uFEFF") else v.trim() }
          val idxTime = header.indexOf("time")
          val idxApp = header.indexOf("app")
          val idxAmount = header.indexOf("amount")
          val idxCurrency = header.indexOf("currency")
          val idxMerchant = header.indexOf("merchant")
          val idxCategory = header.indexOf("category")
          val idxNote = header.indexOf("note")
          val idxConf = header.indexOf("confidence")
          val idxRaw = header.indexOf("raw")

          // Fallback to legacy fixed positions if header is missing/unexpected.
          val useHeader = listOf(idxTime, idxApp, idxAmount, idxCurrency, idxMerchant, idxCategory, idxNote, idxConf, idxRaw).all { it >= 0 }

          for (row in rows.drop(1)) {
            if (row.isEmpty() || row.all { it.isBlank() }) continue

            val fields = row
            val get = { i: Int -> if (i in fields.indices) fields[i] else "" }

            val time = if (useHeader) get(idxTime) else get(0)
            val app = if (useHeader) get(idxApp) else get(1)
            val amountStr = if (useHeader) get(idxAmount) else get(2)
            val currency = if (useHeader) get(idxCurrency) else get(3)
            val merchant = if (useHeader) get(idxMerchant) else get(4)
            val category = if (useHeader) get(idxCategory) else get(5)
            val note = if (useHeader) get(idxNote) else get(6)
            val confStr = if (useHeader) get(idxConf) else get(7)
            val raw = if (useHeader) get(idxRaw) else get(8)

            val entry = try {
              LedgerEntry(
                time = time,
                app = app,
                amount = amountStr.toDoubleOrNull() ?: 0.0,
                currency = currency,
                merchant = merchant,
                category = category,
                note = note,
                confidence = confStr.toDoubleOrNull(),
                raw = raw
              )
            } catch (_: Exception) {
              null
            }

            if (entry != null) entries.add(entry)
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return entries
  }

  /**
   * Minimal CSV parser supporting:
   * - commas
   * - double quotes + escaped quotes
   * - newlines inside quoted fields
   */
  private fun parseCsv(reader: BufferedReader): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val cell = StringBuilder()
    var inQuotes = false

    fun endCell() {
      row.add(cell.toString())
      cell.setLength(0)
    }

    fun endRow() {
      // add last cell even for empty line (caller can skip)
      endCell()
      rows.add(row.toList())
      row.clear()
    }

    var r = reader.read()
    while (r != -1) {
      val ch = r.toChar()
      when {
        ch == '"' -> {
          if (inQuotes) {
            reader.mark(1)
            val next = reader.read()
            if (next == '"'.code) {
              cell.append('"')
            } else {
              inQuotes = false
              if (next != -1) reader.reset()
            }
          } else {
            inQuotes = true
          }
        }
        ch == ',' && !inQuotes -> {
          endCell()
        }
        (ch == '\n' || ch == '\r') && !inQuotes -> {
          // handle CRLF
          if (ch == '\r') {
            reader.mark(1)
            val next = reader.read()
            if (next != '\n'.code && next != -1) reader.reset()
          }
          endRow()
        }
        else -> cell.append(ch)
      }
      r = reader.read()
    }

    // flush last row if file doesn't end with newline
    if (cell.isNotEmpty() || row.isNotEmpty()) {
      endRow()
    }

    return rows
  }
}
