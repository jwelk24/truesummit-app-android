package com.truesummit.android.service

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the AI response models against the kotlinx-serialization compiler
 * plugin going missing.
 *
 * Without the plugin, `@Serializable` compiles fine but generates no
 * serializer, and every decode throws at runtime. AIInsightsService swallows
 * those failures — each call is wrapped in runCatching and returns null or an
 * empty list — so the only symptom is that every AI feature quietly produces
 * nothing. These tests turn that silent failure into a build failure.
 */
class AiResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `category suggestion decodes`() {
        val result = json.decodeFromString<CategorySuggestion>(
            """{"categoryId":"abc-123","confidence":0.82,"reasoning":"Coffee shop"}"""
        )
        assertEquals("abc-123", result.categoryId)
        assertEquals(0.82, result.confidence, 0.001)
    }

    @Test
    fun `weekly digest decodes with its bullet list`() {
        val result = json.decodeFromString<WeeklyDigest>(
            """{"headline":"Steady week","bullets":["a","b"],"suggestion":"Keep going"}"""
        )
        assertEquals("Steady week", result.headline)
        assertEquals(listOf("a", "b"), result.bullets)
    }

    @Test
    fun `anomaly list decodes`() {
        val results = json.decodeFromString<List<AnomalyResult>>(
            """[{"merchant":"Netflix","amount":22.99,"reason":"Price rose","severity":"medium"}]"""
        )
        assertEquals(1, results.size)
        assertEquals("Netflix", results.first().merchant)
    }

    @Test
    fun `savings suggestions decode`() {
        val results = json.decodeFromString<List<SavingsSuggestion>>(
            """[{"title":"Dining","detail":"Cook twice more","estimatedMonthlySavings":60.0}]"""
        )
        assertEquals(60.0, results.first().estimatedMonthlySavings, 0.001)
    }

    @Test
    fun `month recap decodes`() {
        val result = json.decodeFromString<MonthRecapData>(
            """{"headline":"Solid","topWin":"Groceries","topChallenge":"Dining",
                "forNextMonth":"Trim takeout","savingsRate":0.22}"""
        )
        assertEquals("Solid", result.headline)
    }

    @Test
    fun `nl search result decodes with nullable bounds`() {
        val result = json.decodeFromString<NLSearchResult>(
            """{"filterDescription":"coffee last month","merchantKeywords":["starbucks"],
                "categoryKeywords":["coffee"],"minAmount":null,"maxAmount":null,"dayRange":30}"""
        )
        assertEquals(30, result.dayRange)
        assertTrue(result.minAmount == null)
    }

    /** Gemini wraps JSON in prose or code fences; the service strips both. */
    @Test
    fun `unknown fields are tolerated`() {
        val result = json.decodeFromString<CategorySuggestion>(
            """{"categoryId":"x","confidence":0.5,"reasoning":"y","extra":"ignored"}"""
        )
        assertEquals("x", result.categoryId)
    }
}
