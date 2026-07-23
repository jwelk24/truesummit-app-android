package com.truesummit.android.service

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.truesummit.android.data.entity.CategoryEntity
import com.truesummit.android.data.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

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

class AIInsightsService(private val context: Context) {
    // Note: In a real app, the API key should be stored securely (e.g. via backend or local secrets)
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = "YOUR_GEMINI_API_KEY" // Placeholder
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun suggestCategory(
        transaction: TransactionEntity,
        categories: List<CategoryEntity>
    ): CategorySuggestion? {
        val catalog = categories.joinToString("\n") { 
            "${it.id} | ${it.name}" 
        }

        val prompt = """
            You are a budgeting assistant. Pick the single best category for this transaction.
            Return ONLY a JSON object with 'categoryId', 'confidence' (0.0-1.0), and 'reasoning'.
            
            Transaction:
            - Merchant: ${transaction.merchant}
            - Amount: ${transaction.amount}
            - Memo: ${transaction.memo ?: "—"}
            
            Catalog (id | name):
            $catalog
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text?.substringAfter("{")?.substringBeforeLast("}")?.let { "{$it}" }
            jsonText?.let { json.decodeFromString<CategorySuggestion>(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun generateWeeklySummary(transactions: List<TransactionEntity>): WeeklyDigest? {
        val df = SimpleDateFormat("MMM d", Locale.getDefault())
        val txLines = transactions.take(50).joinToString("\n") { 
            "${df.format(it.date)} | ${it.merchant} | ${it.amount}"
        }

        val prompt = """
            Write a short, friendly weekly money digest for a personal-finance app user.
            Return ONLY a JSON object with 'headline', 'bullets' (list of 2-4 observations), and 'suggestion'.
            
            Recent Transactions:
            $txLines
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text?.substringAfter("{")?.substringBeforeLast("}")?.let { "{$it}" }
            jsonText?.let { json.decodeFromString<WeeklyDigest>(it) }
        } catch (e: Exception) {
            null
        }
    }
}
