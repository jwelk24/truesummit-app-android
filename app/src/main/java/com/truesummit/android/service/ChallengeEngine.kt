package com.truesummit.android.service

import android.content.Context
import com.truesummit.android.data.entity.TransactionEntity
import java.math.BigDecimal
import java.util.*

enum class ChallengeKind { NO_SPEND_DAYS, TRIM_CATEGORY, MERCHANT_BREAK, SAVINGS_SPRINT }

data class Challenge(
    val id: String,
    val kind: ChallengeKind,
    val title: String,
    val detail: String,
    val goal: Double,
    val durationDays: Int,
    val startDate: Date = Date()
)

data class ChallengeProgress(
    val challenge: Challenge,
    val current: Double,
    val isComplete: Boolean
)

object ChallengeEngine {
    private const val PREFS = "truesummit_challenges"

    fun progress(challenge: Challenge, transactions: List<TransactionEntity>, now: Date = Date()): ChallengeProgress {
        val msPerDay = 86_400_000L
        val startMs = challenge.startDate.time
        val endMs = startMs + challenge.durationDays * msPerDay
        val windowTx = transactions.filter { it.date.time in startMs..endMs }

        val current: Double = when (challenge.kind) {
            ChallengeKind.NO_SPEND_DAYS -> {
                val daysWithSpend = windowTx
                    .filter { it.amount > BigDecimal.ZERO }
                    .map { dayKey(it.date) }
                    .toSet()
                val totalDays = ((minOf(now.time, endMs) - startMs) / msPerDay).toInt().coerceAtLeast(0)
                (totalDays - daysWithSpend.size).coerceAtLeast(0).toDouble()
            }
            ChallengeKind.TRIM_CATEGORY -> {
                val categoryId = challenge.id.substringAfter("trim_")
                val spent = windowTx
                    .filter { it.categoryId?.toString() == categoryId && it.amount > BigDecimal.ZERO }
                    .sumOf { it.amount.toDouble() }
                // progress = how much saved vs goal (goal is the target spend cap)
                maxOf(0.0, challenge.goal - spent)
            }
            ChallengeKind.MERCHANT_BREAK -> {
                val merchant = challenge.id.substringAfter("merchant_")
                val visits = windowTx.count { it.merchant.contains(merchant, ignoreCase = true) }
                maxOf(0.0, challenge.goal - visits)
            }
            ChallengeKind.SAVINGS_SPRINT -> {
                val income = windowTx.filter { it.amount < BigDecimal.ZERO }.sumOf { it.amount.abs().toDouble() }
                val spend = windowTx.filter { it.amount > BigDecimal.ZERO }.sumOf { it.amount.toDouble() }
                maxOf(0.0, income - spend)
            }
        }

        return ChallengeProgress(
            challenge = challenge,
            current = current,
            isComplete = current >= challenge.goal
        )
    }

    fun suggestions(transactions: List<TransactionEntity>, now: Date = Date()): List<Challenge> {
        val cal = Calendar.getInstance().apply { time = now }
        val msPerDay = 86_400_000L
        val thirtyDaysAgo = Date(now.time - 30 * msPerDay)
        val recent = transactions.filter { it.date.after(thirtyDaysAgo) && it.amount > BigDecimal.ZERO }

        val result = mutableListOf<Challenge>()

        // No-spend days suggestion: aim for 8 no-spend days in 30
        result.add(Challenge(
            id = "no_spend_30",
            kind = ChallengeKind.NO_SPEND_DAYS,
            title = "8 No-Spend Days",
            detail = "Go 8 days this month without spending a single dollar.",
            goal = 8.0,
            durationDays = 30,
            startDate = now
        ))

        // Trim top spending category
        val topCategory = recent
            .groupBy { it.categoryId?.toString() ?: "unknown" }
            .maxByOrNull { (_, txs) -> txs.sumOf { it.amount.toDouble() } }
        if (topCategory != null) {
            val currentSpend = topCategory.value.sumOf { it.amount.toDouble() }
            val target = currentSpend * 0.8
            result.add(Challenge(
                id = "trim_${topCategory.key}",
                kind = ChallengeKind.TRIM_CATEGORY,
                title = "Cut Top Category 20%",
                detail = "Reduce spending in your biggest category to \$${String.format("%.0f", target)} this month.",
                goal = currentSpend - target,
                durationDays = 30,
                startDate = now
            ))
        }

        // Merchant break: most-visited merchant
        val topMerchant = recent
            .groupBy { it.merchant }
            .maxByOrNull { (_, txs) -> txs.size }
        if (topMerchant != null && topMerchant.value.size >= 3) {
            result.add(Challenge(
                id = "merchant_${topMerchant.key}",
                kind = ChallengeKind.MERCHANT_BREAK,
                title = "Break from ${topMerchant.key}",
                detail = "Skip ${topMerchant.key} for 14 days straight.",
                goal = 14.0,
                durationDays = 14,
                startDate = now
            ))
        }

        // Savings sprint
        result.add(Challenge(
            id = "savings_sprint_30",
            kind = ChallengeKind.SAVINGS_SPRINT,
            title = "30-Day Savings Sprint",
            detail = "Save at least \$200 over the next 30 days.",
            goal = 200.0,
            durationDays = 30,
            startDate = now
        ))

        return result
    }

    private fun dayKey(date: Date): String {
        val cal = Calendar.getInstance().apply { time = date }
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }
}

object ChallengeStore {
    private const val PREFS = "truesummit_challenge_store"
    private const val KEY_ACTIVE = "active_challenges"
    private const val KEY_COMPLETED = "completed_challenge_ids"
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun activeChallenges(): List<Challenge> {
        val json = prefs?.getString(KEY_ACTIVE, "[]") ?: return emptyList()
        return parseChallenges(json)
    }

    fun completedIds(): Set<String> =
        prefs?.getStringSet(KEY_COMPLETED, emptySet()) ?: emptySet()

    fun startChallenge(challenge: Challenge) {
        val active = activeChallenges().toMutableList()
        if (active.none { it.id == challenge.id }) {
            active.add(challenge)
            prefs?.edit()?.putString(KEY_ACTIVE, encodeChallenges(active))?.apply()
        }
    }

    fun markCompleted(id: String) {
        val active = activeChallenges().filter { it.id != id }
        val completed = completedIds().toMutableSet().also { it.add(id) }
        prefs?.edit()
            ?.putString(KEY_ACTIVE, encodeChallenges(active))
            ?.putStringSet(KEY_COMPLETED, completed)
            ?.apply()
    }

    fun removeChallenge(id: String) {
        val active = activeChallenges().filter { it.id != id }
        prefs?.edit()?.putString(KEY_ACTIVE, encodeChallenges(active))?.apply()
    }

    private fun parseChallenges(json: String): List<Challenge> {
        if (json == "[]" || json.isBlank()) return emptyList()
        return try {
            json.trim('[', ']').split("|||").filter { it.isNotBlank() }.map { item ->
                val parts = item.split("^^")
                Challenge(
                    id = parts[0],
                    kind = ChallengeKind.valueOf(parts[1]),
                    title = parts[2],
                    detail = parts[3],
                    goal = parts[4].toDouble(),
                    durationDays = parts[5].toInt(),
                    startDate = Date(parts[6].toLong())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun encodeChallenges(list: List<Challenge>): String {
        if (list.isEmpty()) return "[]"
        return "[" + list.joinToString("|||") { c ->
            "${c.id}^^${c.kind.name}^^${c.title}^^${c.detail}^^${c.goal}^^${c.durationDays}^^${c.startDate.time}"
        } + "]"
    }
}
