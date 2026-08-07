package com.truesummit.wear

import org.json.JSONObject

data class BillSummary(
    val name: String,
    val amount: Double,
    val daysUntil: Int
)

data class WatchSnapshot(
    val safeToSpendToday: Double?,
    val safePerDay: Double?,
    val netWorth: Double,
    val budgetRemaining: Double,
    val budgetAssigned: Double,
    val monthLabel: String,
    val upcomingBill: BillSummary?,
    val healthScore: Int?,
    val healthGrade: String?,
    val currencyCode: String = "USD"
) {
    val budgetUsedFraction: Double
        get() = if (budgetAssigned > 0) (1.0 - budgetRemaining / budgetAssigned).coerceIn(0.0, 1.0) else 0.0

    companion object {
        fun fromJson(json: String): WatchSnapshot? = runCatching {
            val obj = JSONObject(json)
            val billObj = obj.optJSONArray("upcomingBills")?.optJSONObject(0)
            WatchSnapshot(
                safeToSpendToday = if (obj.isNull("safeToSpendToday")) null else obj.getDouble("safeToSpendToday"),
                safePerDay = if (obj.isNull("safePerDay")) null else obj.getDouble("safePerDay"),
                netWorth = obj.getDouble("totalAssets") - obj.getDouble("totalLiabilities"),
                budgetRemaining = obj.getDouble("budgetAssigned") - obj.getDouble("budgetSpent"),
                budgetAssigned = obj.getDouble("budgetAssigned"),
                monthLabel = obj.getString("monthLabel"),
                upcomingBill = billObj?.let {
                    BillSummary(
                        name = it.getString("name"),
                        amount = it.getDouble("amount"),
                        daysUntil = it.optInt("daysUntil", 0)
                    )
                },
                healthScore = if (obj.isNull("healthScore")) null else obj.getInt("healthScore"),
                healthGrade = if (obj.isNull("healthGrade")) null else obj.getString("healthGrade"),
                currencyCode = obj.optString("currencyCode", "USD")
            )
        }.getOrNull()
    }
}
