package com.truesummit.android.service

import android.content.Context
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.CategoryRuleEntity
import com.truesummit.android.data.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import java.util.*

object RuleEngine {

    private fun rulesEnabled(): Boolean =
        PremiumManager.currentTier.value == SubscriptionTier.PREMIUM

    fun matches(rule: CategoryRuleEntity, transaction: TransactionEntity): Boolean {
        if (!rule.enabled) return false
        if (rule.pattern.isEmpty()) return false

        val fieldValue = when (rule.matchField) {
            "merchant" -> transaction.merchant
            "memo" -> transaction.memo ?: ""
            else -> transaction.merchant
        }

        val pattern = if (rule.caseSensitive) rule.pattern else rule.pattern.lowercase()
        val target = if (rule.caseSensitive) fieldValue else fieldValue.lowercase()

        return when (rule.matchKind) {
            "contains" -> target.contains(pattern)
            "equals" -> target == pattern
            "startsWith" -> target.startsWith(pattern)
            "endsWith" -> target.endsWith(pattern)
            else -> target.contains(pattern)
        }
    }

    /**
     * Applies all matching rules in priority order, mirroring iOS multi-action behaviour:
     * - Category: fills in if not already set (first match wins).
     * - Rename: first matching rule with a non-blank renameTo claims it.
     * - Tags: accumulate from every matching rule.
     *
     * Returns the updated transaction (may be identical to input if no rule changed it).
     */
    fun applyAll(rules: List<CategoryRuleEntity>, tx: TransactionEntity): TransactionEntity {
        if (!rulesEnabled()) return tx
        val sorted = rules.filter { it.enabled }.sortedBy { it.priority }

        var current = tx
        var categoryClaimed = current.categoryId != null
        var renameClaimed = false

        for (rule in sorted) {
            if (!matches(rule, current)) continue

            var newCategoryId = current.categoryId
            var newMerchant = current.merchant
            var newTags = current.tagList().toMutableList()

            if (!categoryClaimed && rule.categoryId != null) {
                newCategoryId = rule.categoryId
                categoryClaimed = true
            }

            if (!renameClaimed) {
                val rename = rule.renameTo?.trim() ?: ""
                if (rename.isNotEmpty()) {
                    newMerchant = rename
                    renameClaimed = true
                }
            }

            for (tag in rule.addTagList()) {
                if (!newTags.contains(tag)) newTags.add(tag)
            }

            current = current.copy(
                categoryId = newCategoryId,
                merchant = newMerchant,
                tags = newTags.joinToString(",")
            )
        }
        return current
    }

    /** Legacy single-return helper used by ingest paths that only care about category. */
    suspend fun applyRules(rules: List<CategoryRuleEntity>, tx: TransactionEntity, db: AppDatabase): UUID? {
        val result = applyAll(rules, tx)
        if (result != tx) {
            val statsUpdated = rules.filter { it.enabled && matches(it, tx) }
            for (rule in statsUpdated) {
                db.categoryRuleDao().update(
                    rule.copy(timesApplied = rule.timesApplied + 1, lastAppliedAt = Date())
                )
            }
        }
        return result.categoryId
    }

    suspend fun categorizeIfPossible(tx: TransactionEntity, db: AppDatabase) {
        if (!rulesEnabled()) return
        val rules = db.categoryRuleDao().getEnabledRules()
        val updated = applyAll(rules, tx)
        if (updated != tx) db.transactionDao().update(updated)
    }

    suspend fun backfill(context: Context): Int {
        if (!rulesEnabled()) return 0
        val db = AppDatabase.getInstance(context.applicationContext)
        val rules = db.categoryRuleDao().getEnabledRules()
        val transactions = db.transactionDao().getAll().first()
        var hits = 0
        for (tx in transactions) {
            val updated = applyAll(rules, tx)
            if (updated != tx) {
                db.transactionDao().update(updated)
                hits++
            }
        }
        return hits
    }
}
