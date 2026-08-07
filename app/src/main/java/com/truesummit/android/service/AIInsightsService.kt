package com.truesummit.android.service

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.truesummit.android.BuildConfig
import com.truesummit.android.data.entity.BudgetAllocationEntity
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.GoalEntity
import com.truesummit.android.data.entity.TransactionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── Response models ──────────────────────────────────────────────────────────

@Serializable
data class CategorySuggestion(
    val categoryId: String,
    val confidence: Double,
    val reasoning: String
)

@Serializable
data class WeeklyDigest(
    val headline: String,
    val bullets: List<String>,
    val suggestion: String
)

@Serializable
data class MonthRecapData(
    val headline: String,
    val topWin: String,
    val topChallenge: String,
    val forNextMonth: String,
    val savingsRate: Double
)

@Serializable
data class AnomalyResult(
    val merchant: String,
    val amount: Double,
    val reason: String,
    val severity: String // "low" | "medium" | "high"
)

@Serializable
data class SavingsSuggestion(
    val title: String,
    val detail: String,
    val estimatedMonthlySavings: Double
)

@Serializable
data class NLSearchResult(
    val filterDescription: String,
    val merchantKeywords: List<String>,
    val categoryKeywords: List<String>,
    val minAmount: Double?,
    val maxAmount: Double?,
    val dayRange: Int?
)

// ── Service ──────────────────────────────────────────────────────────────────

class AIInsightsService(private val context: Context) {

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val df = SimpleDateFormat("MMM d", Locale.getDefault())
    private val currency = NumberFormat.getCurrencyInstance(Locale.US)

    // ── 1. Smart auto-categorization ─────────────────────────────────────────

    suspend fun suggestCategory(
        transaction: TransactionEntity,
        categories: List<CategoryEntity>
    ): CategorySuggestion? {
        val catalog = categories.joinToString("\n") { "${it.id} | ${it.name}" }
        val prompt = """
            You are a budgeting assistant. Pick the single best category for this transaction.
            Return ONLY valid JSON with keys: categoryId (string), confidence (0.0-1.0), reasoning (string).

            Transaction: merchant="${transaction.merchant}" amount=${transaction.amount} memo="${transaction.memo ?: ""}"

            Categories (id | name):
            $catalog
        """.trimIndent()
        return runCatching {
            val raw = model.generateContent(prompt).text ?: return null
            json.decodeFromString<CategorySuggestion>(extractJson(raw))
        }.getOrNull()
    }

    // ── 2. Weekly digest ─────────────────────────────────────────────────────

    suspend fun generateWeeklySummary(
        transactions: List<TransactionEntity>,
        categoryNames: Map<UUID, String> = emptyMap()
    ): WeeklyDigest? {
        val txLines = transactions.take(60).joinToString("\n") {
            "${df.format(it.date)} | ${it.merchant} | ${currency.format(it.amount)} | ${categoryNames[it.categoryId] ?: "Uncategorized"}"
        }
        val prompt = """
            Write a short, friendly weekly money digest for a personal-finance app user.
            Be specific — use actual merchant names and amounts from the data.
            Return ONLY valid JSON: { "headline": string, "bullets": [2-4 strings], "suggestion": string }

            Transactions (date | merchant | amount | category):
            $txLines
        """.trimIndent()
        return runCatching {
            val raw = model.generateContent(prompt).text ?: return null
            json.decodeFromString<WeeklyDigest>(extractJson(raw))
        }.getOrNull()
    }

    // ── 3. Month recap narrative ─────────────────────────────────────────────

    suspend fun generateMonthRecap(
        transactions: List<TransactionEntity>,
        allocations: List<BudgetAllocationEntity>,
        categoryNames: Map<UUID, String>
    ): MonthRecapData? {
        val income = transactions.filter { it.amount > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
        val spending = transactions.filter { it.amount < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount.abs() }
        val savingsRate = if (income > BigDecimal.ZERO)
            spending.toDouble() / income.toDouble() else 0.0

        val topCategories = transactions
            .filter { it.amount < BigDecimal.ZERO && it.categoryId != null }
            .groupBy { categoryNames[it.categoryId] ?: "Other" }
            .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { a, t -> a + t.amount.abs() } }
            .entries.sortedByDescending { it.value }.take(5)
            .joinToString(", ") { "${it.key}: ${currency.format(it.value)}" }

        val prompt = """
            Write a friendly end-of-month financial recap for a budgeting app.
            Return ONLY valid JSON: { "headline": string, "topWin": string, "topChallenge": string, "forNextMonth": string, "savingsRate": number }

            Stats:
            - Income: ${currency.format(income)}
            - Spending: ${currency.format(spending)}
            - Spending rate: ${(savingsRate * 100).toInt()}% of income
            - Top categories: $topCategories
        """.trimIndent()
        return runCatching {
            val raw = model.generateContent(prompt).text ?: return null
            json.decodeFromString<MonthRecapData>(extractJson(raw))
        }.getOrNull()
    }

    // ── 4. Anomaly detection ─────────────────────────────────────────────────

    suspend fun detectAnomalies(
        recentTransactions: List<TransactionEntity>,
        historicalTransactions: List<TransactionEntity>
    ): List<AnomalyResult> {
        val recentLines = recentTransactions.take(30).joinToString("\n") {
            "${df.format(it.date)} | ${it.merchant} | ${currency.format(it.amount)}"
        }
        val historicalSummary = historicalTransactions
            .filter { it.amount < BigDecimal.ZERO }
            .groupBy { it.merchant }
            .mapValues { (_, txs) ->
                txs.fold(BigDecimal.ZERO) { a, t -> a + t.amount.abs() } / BigDecimal(txs.size)
            }
            .entries.sortedByDescending { it.value }.take(20)
            .joinToString(", ") { "${it.key}: avg ${currency.format(it.value)}" }

        val prompt = """
            Identify unusual or suspicious transactions. Consider: unexpected amounts, duplicate charges, unfamiliar merchants, price changes on recurring bills.
            Return ONLY valid JSON array (may be empty): [{ "merchant": string, "amount": number, "reason": string, "severity": "low"|"medium"|"high" }]

            Recent transactions:
            $recentLines

            Historical averages by merchant:
            $historicalSummary
        """.trimIndent()
        return runCatching {
            val raw = model.generateContent(prompt).text ?: return emptyList()
            val arr = extractJsonArray(raw)
            json.decodeFromString<List<AnomalyResult>>(arr)
        }.getOrDefault(emptyList())
    }

    // ── 5. Savings suggestions ───────────────────────────────────────────────

    suspend fun generateSavingsSuggestions(
        transactions: List<TransactionEntity>,
        categoryNames: Map<UUID, String>,
        goals: List<GoalEntity> = emptyList()
    ): List<SavingsSuggestion> {
        val spendByCategory = transactions
            .filter { it.amount < BigDecimal.ZERO && it.categoryId != null }
            .groupBy { categoryNames[it.categoryId] ?: "Other" }
            .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { a, t -> a + t.amount.abs() } }
            .entries.sortedByDescending { it.value }
            .joinToString("\n") { "${it.key}: ${currency.format(it.value)}/mo" }

        val goalSummary = goals.take(3).joinToString(", ") {
            "target ${currency.format(it.targetAmount)}"
        }.ifEmpty { "none" }

        val prompt = """
            Suggest 3 realistic ways this user could save money based on their spending.
            Be specific — name actual categories and amounts. Keep tone encouraging, not judgmental.
            Return ONLY valid JSON array: [{ "title": string, "detail": string, "estimatedMonthlySavings": number }]

            Monthly spending by category:
            $spendByCategory

            Active savings goals: $goalSummary
        """.trimIndent()
        return runCatching {
            val raw = model.generateContent(prompt).text ?: return emptyList()
            json.decodeFromString<List<SavingsSuggestion>>(extractJsonArray(raw))
        }.getOrDefault(emptyList())
    }

    // ── 6. Natural language search ───────────────────────────────────────────

    suspend fun parseNaturalLanguageSearch(query: String): NLSearchResult? {
        val prompt = """
            Parse this natural language transaction search query into filter parameters.
            Return ONLY valid JSON: {
              "filterDescription": string,
              "merchantKeywords": [strings],
              "categoryKeywords": [strings],
              "minAmount": number or null,
              "maxAmount": number or null,
              "dayRange": number or null
            }

            Query: "$query"

            Examples:
            "coffee last month" → categoryKeywords: ["coffee", "cafe"], dayRange: 30
            "over $50 at restaurants" → categoryKeywords: ["dining", "restaurant"], minAmount: 50
            "Amazon last week" → merchantKeywords: ["Amazon"], dayRange: 7
        """.trimIndent()
        return runCatching {
            val raw = model.generateContent(prompt).text ?: return null
            json.decodeFromString<NLSearchResult>(extractJson(raw))
        }.getOrNull()
    }

    // ── 7. Conversational budget coach ───────────────────────────────────────

    private val chatSession by lazy { model.startChat() }

    suspend fun chatWithCoach(
        userMessage: String,
        financialContext: String
    ): String {
        val contextPrompt = if (financialContext.isNotBlank()) {
            "You are TrueSummit's friendly financial coach. Here is the user's current financial snapshot:\n$financialContext\n\nNow answer their question concisely (2-4 sentences max).\n\nUser: $userMessage"
        } else {
            "You are TrueSummit's friendly financial coach. Answer concisely (2-4 sentences max).\n\nUser: $userMessage"
        }
        return runCatching {
            chatSession.sendMessage(contextPrompt).text ?: "I couldn't generate a response right now."
        }.getOrDefault("Something went wrong. Please try again.")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
    }

    private fun extractJsonArray(raw: String): String {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else "[]"
    }
}
