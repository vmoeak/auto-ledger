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

  fun readAll(context: Context, uri: Uri): List<LedgerEntry> {
    val entries = mutableListOf<LedgerEntry>()
    try {
      context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
          var lineNum = 0
          reader.forEachLine { line ->
            lineNum++
            // Skip header line and BOM
            if (lineNum == 1) return@forEachLine

            val entry = parseLine(line)
            if (entry != null) {
              entries.add(entry)
            }
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return entries
  }

  private fun parseLine(line: String): LedgerEntry? {
    if (line.isBlank()) return null

    val fields = parseCsvLine(line)
    if (fields.size < 9) return null

    return try {
      LedgerEntry(
        time = fields[0],
        app = fields[1],
        amount = fields[2].toDoubleOrNull() ?: 0.0,
        currency = fields[3],
        merchant = fields[4],
        category = fields[5],
        note = fields[6],
        confidence = fields[7].toDoubleOrNull(),
        raw = fields[8]
      )
    } catch (e: Exception) {
      null
    }
  }

  private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < line.length) {
      val c = line[i]
      when {
        c == '"' && !inQuotes -> {
          inQuotes = true
        }
        c == '"' && inQuotes -> {
          if (i + 1 < line.length && line[i + 1] == '"') {
            current.append('"')
            i++
          } else {
            inQuotes = false
          }
        }
        c == ',' && !inQuotes -> {
          result.add(current.toString())
          current = StringBuilder()
        }
        else -> {
          current.append(c)
        }
      }
      i++
    }
    result.add(current.toString())
    return result
  }
}
