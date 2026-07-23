package com.truesummit.android.service

import com.truesummit.android.data.entity.InvestmentHoldingEntity

object InvestmentAllocationService {

    data class Slice(
        val label: String,
        val value: Double,
        val fraction: Double
    )

    fun compute(holdings: List<InvestmentHoldingEntity>): List<Slice> {
        val totals = mutableMapOf<String, Double>()
        for (holding in holdings) {
            val value = holding.institutionValue.toDouble()
            if (value <= 0) continue
            val assetClass = assetClass(holding)
            totals[assetClass] = (totals[assetClass] ?: 0.0) + value
        }
        val grand = totals.values.sum()
        if (grand <= 0) return emptyList()
        return totals
            .map { (label, value) -> Slice(label, value, value / grand) }
            .sortedByDescending { it.value }
    }

    private fun assetClass(holding: InvestmentHoldingEntity): String {
        if (holding.isCashEquivalent) return "Cash"
        return when (holding.securityType?.lowercase()) {
            "equity" -> "Stocks"
            "etf" -> "ETFs"
            "mutual fund", "mutual_fund" -> "Funds"
            "fixed income", "fixed_income" -> "Bonds"
            "cash" -> "Cash"
            "cryptocurrency" -> "Crypto"
            "derivative" -> "Options"
            else -> holding.securityType?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "Other"
        }
    }
}
