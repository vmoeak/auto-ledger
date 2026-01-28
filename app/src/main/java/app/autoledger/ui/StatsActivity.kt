package app.autoledger.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.autoledger.R
import app.autoledger.core.AppConfig
import app.autoledger.core.LedgerEntry
import app.autoledger.core.LedgerReader
import java.text.SimpleDateFormat
import java.util.*

class StatsActivity : AppCompatActivity() {

  private lateinit var cfg: AppConfig

  private lateinit var btnDay: Button
  private lateinit var btnMonth: Button
  private lateinit var btnYear: Button
  private lateinit var btnPrev: Button
  private lateinit var btnNext: Button
  private lateinit var tvPeriod: TextView
  private lateinit var tvTotalExpense: TextView
  private lateinit var tvTotalIncome: TextView
  private lateinit var tvCount: TextView
  private lateinit var merchantList: LinearLayout

  private var allEntries: List<LedgerEntry> = emptyList()

  private enum class PeriodType { DAY, MONTH, YEAR }
  private var periodType = PeriodType.MONTH
  private var currentCalendar: Calendar = Calendar.getInstance()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_stats)

    cfg = AppConfig(this)

    btnDay = findViewById(R.id.btnDay)
    btnMonth = findViewById(R.id.btnMonth)
    btnYear = findViewById(R.id.btnYear)
    btnPrev = findViewById(R.id.btnPrev)
    btnNext = findViewById(R.id.btnNext)
    tvPeriod = findViewById(R.id.tvPeriod)
    tvTotalExpense = findViewById(R.id.tvTotalExpense)
    tvTotalIncome = findViewById(R.id.tvTotalIncome)
    tvCount = findViewById(R.id.tvCount)
    merchantList = findViewById(R.id.merchantList)

    btnDay.setOnClickListener { setPeriodType(PeriodType.DAY) }
    btnMonth.setOnClickListener { setPeriodType(PeriodType.MONTH) }
    btnYear.setOnClickListener { setPeriodType(PeriodType.YEAR) }
    btnPrev.setOnClickListener { navigate(-1) }
    btnNext.setOnClickListener { navigate(1) }

    loadData()
  }

  private fun loadData() {
    val uri = cfg.ledgerUri
    if (uri == null) {
      Toast.makeText(this, "No ledger file selected", Toast.LENGTH_SHORT).show()
      return
    }

    allEntries = LedgerReader.readAll(this, uri)
    if (allEntries.isEmpty()) {
      Toast.makeText(this, "No data found", Toast.LENGTH_SHORT).show()
    }

    updateStats()
  }

  private fun setPeriodType(type: PeriodType) {
    periodType = type
    updateButtonStyles()
    updateStats()
  }

  private fun updateButtonStyles() {
    val normalBg = android.R.drawable.btn_default
    val selectedBg = android.R.drawable.btn_default

    btnDay.alpha = if (periodType == PeriodType.DAY) 1.0f else 0.6f
    btnMonth.alpha = if (periodType == PeriodType.MONTH) 1.0f else 0.6f
    btnYear.alpha = if (periodType == PeriodType.YEAR) 1.0f else 0.6f
  }

  private fun navigate(direction: Int) {
    when (periodType) {
      PeriodType.DAY -> currentCalendar.add(Calendar.DAY_OF_MONTH, direction)
      PeriodType.MONTH -> currentCalendar.add(Calendar.MONTH, direction)
      PeriodType.YEAR -> currentCalendar.add(Calendar.YEAR, direction)
    }
    updateStats()
  }

  private fun updateStats() {
    val periodStr = when (periodType) {
      PeriodType.DAY -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentCalendar.time)
      PeriodType.MONTH -> SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentCalendar.time)
      PeriodType.YEAR -> SimpleDateFormat("yyyy", Locale.getDefault()).format(currentCalendar.time)
    }
    tvPeriod.text = periodStr

    val filtered = filterByPeriod(allEntries, periodStr)
    displayStats(filtered)
  }

  private fun filterByPeriod(entries: List<LedgerEntry>, periodStr: String): List<LedgerEntry> {
    return entries.filter { entry ->
      entry.time.startsWith(periodStr)
    }
  }

  private fun displayStats(entries: List<LedgerEntry>) {
    var totalExpense = 0.0
    var totalIncome = 0.0
    val merchantTotals = mutableMapOf<String, Double>()

    for (entry in entries) {
      if (entry.amount < 0) {
        totalExpense += entry.amount
      } else {
        totalIncome += entry.amount
      }

      val merchant = entry.merchant.ifBlank { "Unknown" }
      merchantTotals[merchant] = (merchantTotals[merchant] ?: 0.0) + entry.amount
    }

    tvTotalExpense.text = String.format("%.2f", totalExpense)
    tvTotalIncome.text = String.format("+%.2f", totalIncome)
    tvCount.text = entries.size.toString()

    // Display merchant breakdown
    merchantList.removeAllViews()
    val sortedMerchants = merchantTotals.entries.sortedBy { it.value }

    for ((merchant, total) in sortedMerchants) {
      val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 8, 0, 8)
      }

      val nameView = TextView(this).apply {
        text = merchant
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }

      val amountView = TextView(this).apply {
        text = String.format("%.2f", total)
        gravity = Gravity.END
        setTypeface(null, Typeface.BOLD)
        setTextColor(if (total < 0) 0xFFD32F2F.toInt() else 0xFF388E3C.toInt())
      }

      row.addView(nameView)
      row.addView(amountView)
      merchantList.addView(row)
    }

    if (merchantTotals.isEmpty()) {
      val emptyView = TextView(this).apply {
        text = "No transactions in this period"
        setPadding(0, 16, 0, 16)
        gravity = Gravity.CENTER
      }
      merchantList.addView(emptyView)
    }
  }
}
