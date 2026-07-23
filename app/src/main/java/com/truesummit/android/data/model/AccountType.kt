package com.truesummit.android.data.model

enum class AccountType(val displayName: String) {
    CHECKING("Checking"),
    SAVINGS("Savings"),
    CREDIT_CARD("Credit Card"),
    LOAN("Loan"),
    INVESTMENT("Investment"),
    RETIREMENT("Retirement"),
    MANUAL_ASSET("Manual Asset");

    val isAsset: Boolean
        get() = when (this) {
            CHECKING, SAVINGS, INVESTMENT, RETIREMENT, MANUAL_ASSET -> true
            CREDIT_CARD, LOAN -> false
        }
}
