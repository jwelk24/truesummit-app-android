package com.truesummit.android.service

import android.content.Context
import com.truesummit.android.data.entity.TransactionEntity
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

enum class ReportRange(val displayName: String, val monthsBack: Int?) {
    THIS_MONTH("This Month", 0),
    LAST_MONTH("Last Month", 1),
    LAST_3("Last 3 Months", 3),
    LAST_6("Last 6 Months", 6),
    LAST_12("Last 12 Months", 12),
    YEAR_TO_DATE("Year to Date", 12),
    CUSTOM("Custom...", null)
}

enum class ReportCompareMode(val displayName: String) {
    OFF("Off"),
    PREVIOUS("Previous Period"),
    YEAR_AGO("Year Ago")
}

data class ReportPeriod(val start: Date, val end: Date) {
    val label: String
        get() {
            val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            return "${df.format(start)} - ${df.format(end)}"
        }

    fun comparisonPeriod(mode: ReportCompareMode): ReportPeriod? {
        val durationMs = end.time - start.time
        return when (mode) {
            ReportCompareMode.OFF -> null
            ReportCompareMode.PREVIOUS -> ReportPeriod(
                start = Date(start.time - durationMs - 86_400_000L),
                end = Date(start.time - 86_400_000L)
            )
            ReportCompareMode.YEAR_AGO -> {
                val cal = Calendar.getInstance()
                cal.time = start
                cal.add(Calendar.YEAR, -1)
                val s = cal.time
                cal.time = end
                cal.add(Calendar.YEAR, -1)
                ReportPeriod(start = s, end = cal.time)
            }
        }
    }
}

data class ReportSummary(
    val period: ReportPeriod,
    val totalIncome: BigDecimal,
    val totalSpending: BigDecimal,
    val byCategory: List<Pair<String, BigDecimal>>,
    val transactionCount: Int
) {
    val netWorthChange: BigDecimal get() = totalIncome.subtract(totalSpending)
}

object ReportsService {

    fun resolvePeriod(range: ReportRange, customStart: Date?, customEnd: Date?): ReportPeriod {
        val calendar = Calendar.getInstance()
        val now = Date()
        
        // End of today
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfToday = calendar.time

        return when (range) {
            ReportRange.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                ReportPeriod(calendar.time, endOfToday)
            }
            ReportRange.LAST_MONTH -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = calendar.time
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                ReportPeriod(start, calendar.time)
            }
            ReportRange.LAST_3, ReportRange.LAST_6, ReportRange.LAST_12 -> {
                calendar.add(Calendar.MONTH, -(range.monthsBack ?: 1))
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                ReportPeriod(calendar.time, endOfToday)
            }
            ReportRange.YEAR_TO_DATE -> {
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                ReportPeriod(calendar.time, endOfToday)
            }
            ReportRange.CUSTOM -> {
                ReportPeriod(customStart ?: now, customEnd ?: now)
            }
        }
    }

    fun buildSummary(transactions: List<TransactionEntity>, period: ReportPeriod, categories: Map<UUID, String>): ReportSummary {
        var income = BigDecimal.ZERO
        var spending = BigDecimal.ZERO
        val byCat = mutableMapOf<String, BigDecimal>()
        var count = 0

        for (tx in transactions) {
            if (tx.date.after(period.start) && tx.date.before(period.end)) {
                count++
                if (tx.amount > BigDecimal.ZERO) {
                    income = income.add(tx.amount)
                } else {
                    val abs = tx.amount.abs()
                    spending = spending.add(abs)
                    val catName = categories[tx.categoryId] ?: "Uncategorized"
                    byCat[catName] = (byCat[catName] ?: BigDecimal.ZERO).add(abs)
                }
            }
        }

        return ReportSummary(
            period = period,
            totalIncome = income,
            totalSpending = spending,
            byCategory = byCat.toList().sortedByDescending { it.second },
            transactionCount = count
        )
    }

    fun exportToCSV(context: Context, transactions: List<TransactionEntity>, period: ReportPeriod): File? {
        val filtered = transactions.filter { it.date.after(period.start) && it.date.before(period.end) }
            .sortedBy { it.date }
        
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val header = "date,merchant,amount,memo,cleared\n"
        val rows = filtered.joinToString("\n") { tx ->
            "${df.format(tx.date)},\"${tx.merchant.replace("\"", "\"\"")}\",${tx.amount},\"${(tx.memo ?: "").replace("\"", "\"\"")}\",${tx.cleared}"
        }

        return try {
            val fileName = "truesummit_export_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            file.writeText(header + rows)
            file
        } catch (e: Exception) {
            null
        }
    }
}
