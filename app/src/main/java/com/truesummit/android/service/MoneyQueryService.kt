package com.truesummit.android.service

import com.truesummit.android.data.entity.TransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

/**
 * On-device natural-language spending query engine.
 * Maps a plain-English question to a structured MoneyQuery and runs it
 * against the transaction list — no network, no LLM.
 */
object MoneyQueryService {

    enum class Flow { SPENDING, INCOME, BOTH }
    enum class Timeframe { THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, LAST_YEAR, ALL_TIME }
    enum class Aggregation { TOTAL, AVERAGE, COUNT, LIST }

    data class MoneyQuery(
        val keyword: String?,
        val flow: Flow,
        val timeframe: Timeframe,
        val aggregation: Aggregation
    )

    data class QueryResult(
        val answer: String,
        val transactions: List<TransactionEntity>
    )

    // ── Parser ────────────────────────────────────────────────────────────────

    fun parse(question: String): MoneyQuery {
        val q = question.lowercase().trim()

        val flow = when {
            listOf("income", "earned", "made", "received", "paycheck", "salary").any { q.contains(it) } -> Flow.INCOME
            listOf("spent", "spend", "spending", "cost", "charged", "paid", "expense").any { q.contains(it) } -> Flow.SPENDING
            else -> Flow.BOTH
        }

        val timeframe = when {
            listOf("last year", "past year", "this year", "ytd").any { q.contains(it) } -> Timeframe.LAST_YEAR
            listOf("last 3 month", "past 3 month", "3 month", "three month", "quarter").any { q.contains(it) } -> Timeframe.LAST_3_MONTHS
            listOf("last month", "previous month").any { q.contains(it) } -> Timeframe.LAST_MONTH
            listOf("this month", "current month", "so far", "month").any { q.contains(it) } -> Timeframe.THIS_MONTH
            listOf("ever", "all time", "always", "total", "lifetime").any { q.contains(it) } -> Timeframe.ALL_TIME
            else -> Timeframe.THIS_MONTH
        }

        val aggregation = when {
            listOf("average", "avg", "mean", "typical", "usually").any { q.contains(it) } -> Aggregation.AVERAGE
            listOf("how many", "count", "number of", "times").any { q.contains(it) } -> Aggregation.COUNT
            listOf("list", "show", "which", "what are").any { q.contains(it) } -> Aggregation.LIST
            else -> Aggregation.TOTAL
        }

        // Extract keyword — strip known question words and pick the remainder
        val stopWords = setOf(
            "how", "much", "did", "do", "i", "my", "on", "in", "at", "the", "a", "an",
            "spend", "spent", "spending", "income", "earned", "made", "this", "last", "month",
            "year", "quarter", "total", "average", "count", "list", "show", "me", "was", "is",
            "what", "many", "number", "of", "times", "per", "for", "have", "had", "all", "time"
        )
        val keyword = q
            .replace(Regex("[?.,!]"), "")
            .split(" ")
            .filter { it.isNotBlank() && it !in stopWords }
            .joinToString(" ")
            .trim()
            .ifBlank { null }

        return MoneyQuery(keyword, flow, timeframe, aggregation)
    }

    // ── Executor ──────────────────────────────────────────────────────────────

    fun execute(query: MoneyQuery, transactions: List<TransactionEntity>, categoryNames: Map<UUID, String>): QueryResult {
        val now = Calendar.getInstance()
        val filtered = transactions.filter { tx ->
            // Flow filter
            val matchesFlow = when (query.flow) {
                Flow.SPENDING -> tx.amount < BigDecimal.ZERO
                Flow.INCOME -> tx.amount > BigDecimal.ZERO
                Flow.BOTH -> true
            }
            if (!matchesFlow) return@filter false

            // Timeframe filter
            val txCal = Calendar.getInstance().apply { time = tx.date }
            val inWindow = when (query.timeframe) {
                Timeframe.THIS_MONTH ->
                    txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    txCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                Timeframe.LAST_MONTH -> {
                    val lm = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                    txCal.get(Calendar.YEAR) == lm.get(Calendar.YEAR) &&
                    txCal.get(Calendar.MONTH) == lm.get(Calendar.MONTH)
                }
                Timeframe.LAST_3_MONTHS -> {
                    val cut = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }
                    !tx.date.before(cut.time)
                }
                Timeframe.LAST_YEAR -> {
                    val cut = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
                    !tx.date.before(cut.time)
                }
                Timeframe.ALL_TIME -> true
            }
            if (!inWindow) return@filter false

            // Keyword filter — match against merchant or category name
            val kw = query.keyword
            if (kw != null && kw.length > 1) {
                val merchant = MerchantCleaner.clean(tx.merchant).lowercase()
                val catName = categoryNames[tx.categoryId]?.lowercase() ?: ""
                if (!merchant.contains(kw) && !catName.contains(kw)) return@filter false
            }

            true
        }

        val amounts = filtered.map { it.amount.abs() }
        val total = amounts.fold(BigDecimal.ZERO, BigDecimal::add)
        val periodLabel = when (query.timeframe) {
            Timeframe.THIS_MONTH -> "this month"
            Timeframe.LAST_MONTH -> "last month"
            Timeframe.LAST_3_MONTHS -> "over the last 3 months"
            Timeframe.LAST_YEAR -> "over the last year"
            Timeframe.ALL_TIME -> "all time"
        }
        val kwLabel = query.keyword?.let { " on $it" } ?: ""
        val flowLabel = when (query.flow) {
            Flow.SPENDING -> "spent"
            Flow.INCOME -> "earned"
            Flow.BOTH -> "transacted"
        }

        if (filtered.isEmpty()) {
            return QueryResult("No transactions found$kwLabel $periodLabel.", emptyList())
        }

        val answer = when (query.aggregation) {
            Aggregation.TOTAL -> {
                "You $flowLabel ${formatMoney(total)}$kwLabel $periodLabel."
            }
            Aggregation.AVERAGE -> {
                val avg = total.divide(BigDecimal(filtered.size), 2, RoundingMode.HALF_UP)
                "Your average transaction$kwLabel $periodLabel was ${formatMoney(avg)} (${filtered.size} transactions, ${formatMoney(total)} total)."
            }
            Aggregation.COUNT -> {
                "${filtered.size} transaction${if (filtered.size == 1) "" else "s"}$kwLabel $periodLabel totaling ${formatMoney(total)}."
            }
            Aggregation.LIST -> {
                val top = filtered.sortedByDescending { it.date }.take(5)
                val lines = top.joinToString("\n") { tx ->
                    val df = java.text.SimpleDateFormat("MMM d", Locale.getDefault())
                    "• ${MerchantCleaner.clean(tx.merchant)} — ${formatMoney(tx.amount.abs())} (${df.format(tx.date)})"
                }
                val more = if (filtered.size > 5) "\n…and ${filtered.size - 5} more." else ""
                lines + more
            }
        }

        return QueryResult(answer, filtered)
    }

    private fun formatMoney(amount: BigDecimal): String {
        return "\$${"%,.2f".format(amount.toDouble())}"
    }
}
