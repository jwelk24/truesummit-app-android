package com.truesummit.android.ui.insights

import com.truesummit.android.service.MoneyQueryService
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.service.AIInsightsService
import com.truesummit.android.service.AiTurn
import com.truesummit.android.service.AnomalyResult
import com.truesummit.android.service.SavingsSuggestion
import com.truesummit.android.service.WeeklyDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*

class AIInsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application, AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    private val ai = AIInsightsService(application)

    // ── Weekly digest ─────────────────────────────────────────────────────────
    private val _digest = MutableStateFlow<WeeklyDigest?>(null)
    val digest: StateFlow<WeeklyDigest?> = _digest
    private val _isGeneratingDigest = MutableStateFlow(false)
    val isGeneratingDigest: StateFlow<Boolean> = _isGeneratingDigest

    // ── Smart categorize ──────────────────────────────────────────────────────
    private val _isCategorizing = MutableStateFlow(false)
    val isCategorizing: StateFlow<Boolean> = _isCategorizing
    private val _categorizeResult = MutableStateFlow<String?>(null)
    val categorizeResult: StateFlow<String?> = _categorizeResult

    // ── NL search / ask ───────────────────────────────────────────────────────
    private val _queryResult = MutableStateFlow<String?>(null)
    val queryResult: StateFlow<String?> = _queryResult
    private val _isQuerying = MutableStateFlow(false)
    val isQuerying: StateFlow<Boolean> = _isQuerying

    // ── Anomaly detection ─────────────────────────────────────────────────────
    private val _anomalies = MutableStateFlow<List<AnomalyResult>>(emptyList())
    val anomalies: StateFlow<List<AnomalyResult>> = _anomalies
    private val _isDetectingAnomalies = MutableStateFlow(false)
    val isDetectingAnomalies: StateFlow<Boolean> = _isDetectingAnomalies

    // ── Savings suggestions ───────────────────────────────────────────────────
    private val _savingsSuggestions = MutableStateFlow<List<SavingsSuggestion>>(emptyList())
    val savingsSuggestions: StateFlow<List<SavingsSuggestion>> = _savingsSuggestions
    private val _isLoadingSuggestions = MutableStateFlow(false)
    val isLoadingSuggestions: StateFlow<Boolean> = _isLoadingSuggestions

    // ── Budget coach chat ─────────────────────────────────────────────────────
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory
    private val _isCoachTyping = MutableStateFlow(false)
    val isCoachTyping: StateFlow<Boolean> = _isCoachTyping

    // ── Actions ───────────────────────────────────────────────────────────────

    fun generateDigest() {
        viewModelScope.launch {
            _isGeneratingDigest.value = true
            try {
                val txs = db.transactionDao().getAll().first()
                val cats = db.categoryDao().getCategories().first().associate { it.id to it.name }
                _digest.value = ai.generateWeeklySummary(txs, cats)
            } catch (_: Exception) {
            } finally {
                _isGeneratingDigest.value = false
            }
        }
    }

    fun askQuery(question: String) {
        viewModelScope.launch {
            _isQuerying.value = true
            _queryResult.value = null
            try {
                val txs = db.transactionDao().getAll().first()
                val cats = db.categoryDao().getCategories().first()
                val catNames = cats.associate { it.id to it.name }

                // Try the on-device engine first. It answers the common shapes
                // ("groceries this month", "income last month") instantly, with
                // no network and nothing leaving the device — Gemini is only
                // worth a round-trip for phrasing the local parser cannot read.
                val local = MoneyQueryService.execute(
                    MoneyQueryService.parse(question), txs, catNames
                )
                if (local.transactions.isNotEmpty()) {
                    _queryResult.value = local.answer
                    return@launch
                }

                // Nothing matched locally. That is usually odd phrasing rather
                // than an empty ledger, so let Gemini reinterpret it.
                val parsed = ai.parseNaturalLanguageSearch(question)
                if (parsed != null) {
                    val cutoff = if (parsed.dayRange != null) {
                        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -(parsed.dayRange)); c.time
                    } else null

                    val matched = txs.filter { tx ->
                        if (tx.amount >= BigDecimal.ZERO) return@filter false
                        val amt = tx.amount.abs().toDouble()
                        if (parsed.minAmount != null && amt < parsed.minAmount) return@filter false
                        if (parsed.maxAmount != null && amt > parsed.maxAmount) return@filter false
                        if (cutoff != null && tx.date < cutoff) return@filter false
                        val merchant = tx.merchant.lowercase()
                        val catName = (catNames[tx.categoryId] ?: "").lowercase()
                        val merchantMatch = parsed.merchantKeywords.isEmpty() ||
                                parsed.merchantKeywords.any { merchant.contains(it.lowercase()) }
                        val catMatch = parsed.categoryKeywords.isEmpty() ||
                                parsed.categoryKeywords.any { catName.contains(it.lowercase()) || merchant.contains(it.lowercase()) }
                        merchantMatch && catMatch
                    }

                    _queryResult.value = if (matched.isEmpty()) {
                        "No transactions found for: \"$question\""
                    } else {
                        val total = matched.fold(BigDecimal.ZERO) { a, t -> a + t.amount.abs() }
                        "${matched.size} transaction${if (matched.size == 1) "" else "s"} — total \$${"%,.2f".format(total)}\n" +
                                matched.take(5).joinToString("\n") { "• ${it.merchant}: \$${"%,.2f".format(it.amount.abs())}" } +
                                if (matched.size > 5) "\n…and ${matched.size - 5} more" else ""
                    }
                } else {
                    // Gemini unreachable or unparseable — the local engine still
                    // read the question well enough to say something true, so
                    // show that rather than a bare failure.
                    _queryResult.value = local.answer
                }
            } catch (e: Exception) {
                _queryResult.value = "Couldn't process that — try rephrasing."
            } finally {
                _isQuerying.value = false
            }
        }
    }

    fun runSmartCategorize() {
        viewModelScope.launch {
            _isCategorizing.value = true
            _categorizeResult.value = null
            try {
                val uncategorized = db.transactionDao().getAll().first().filter { it.categoryId == null && it.amount < BigDecimal.ZERO }
                val categories = db.categoryDao().getCategories().first()
                var updatedCount = 0
                for (tx in uncategorized) {
                    val suggestion = ai.suggestCategory(tx, categories)
                    if (suggestion != null && suggestion.confidence > 0.5) {
                        val cat = categories.find { it.id.toString() == suggestion.categoryId }
                        if (cat != null) {
                            db.transactionDao().update(tx.copy(categoryId = cat.id))
                            updatedCount++
                        }
                    }
                }
                _categorizeResult.value = if (updatedCount == 0)
                    "Nothing to categorize — every transaction already has a category."
                else
                    "Categorized $updatedCount transaction${if (updatedCount == 1) "" else "s"}."
            } catch (e: Exception) {
                _categorizeResult.value = "Error: ${e.localizedMessage}"
            } finally {
                _isCategorizing.value = false
            }
        }
    }

    fun detectAnomalies() {
        viewModelScope.launch {
            _isDetectingAnomalies.value = true
            try {
                val all = db.transactionDao().getAll().first()
                val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.time
                val recent = all.filter { it.date >= cutoff }
                _anomalies.value = ai.detectAnomalies(recent, all)
            } catch (_: Exception) {
                _anomalies.value = emptyList()
            } finally {
                _isDetectingAnomalies.value = false
            }
        }
    }

    fun loadSavingsSuggestions() {
        viewModelScope.launch {
            _isLoadingSuggestions.value = true
            try {
                val txs = db.transactionDao().getAll().first()
                val cats = db.categoryDao().getCategories().first().associate { it.id to it.name }
                val goals = db.goalDao().getAllGoals().first()
                _savingsSuggestions.value = ai.generateSavingsSuggestions(txs, cats, goals)
            } catch (_: Exception) {
                _savingsSuggestions.value = emptyList()
            } finally {
                _isLoadingSuggestions.value = false
            }
        }
    }

    fun sendCoachMessage(message: String) {
        viewModelScope.launch {
            // Snapshot the history before appending, so it becomes the prior
            // turns rather than including the message we are about to send.
            val priorTurns = _chatHistory.value.map { AiTurn(it.text, it.isUser) }
            val userMsg = ChatMessage(text = message, isUser = true)
            _chatHistory.value = _chatHistory.value + userMsg
            _isCoachTyping.value = true
            try {
                val txs = db.transactionDao().getAll().first()
                val accounts = db.accountDao().getAll().first()
                val cutoff = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.time
                val recentSpend = txs.filter { it.date >= cutoff && it.amount < BigDecimal.ZERO }
                    .fold(BigDecimal.ZERO) { a, t -> a + t.amount.abs() }
                val totalBalance = accounts.fold(BigDecimal.ZERO) { a, acc -> a + acc.balance }

                val context = "Total balance: \$${"%,.2f".format(totalBalance)} | " +
                        "Spending last 30 days: \$${"%,.2f".format(recentSpend)} | " +
                        "Accounts: ${accounts.size}"

                val reply = ai.chatWithCoach(message, context, priorTurns)
                _chatHistory.value = _chatHistory.value + ChatMessage(text = reply, isUser = false)
            } catch (e: Exception) {
                _chatHistory.value = _chatHistory.value + ChatMessage(
                    text = "Something went wrong. Please try again.", isUser = false
                )
            } finally {
                _isCoachTyping.value = false
            }
        }
    }
}

data class ChatMessage(val text: String, val isUser: Boolean, val id: UUID = UUID.randomUUID())
