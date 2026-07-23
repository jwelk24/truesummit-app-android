package com.truesummit.android.ui.onboarding

import android.content.Context
import android.content.SharedPreferences

object OnboardingState {
    private const val PREFS = "truesummit_prefs"
    private const val KEY_WELCOME_DONE = "onboarding.welcomeDone"
    private const val KEY_CHECKLIST_DISMISSED = "onboarding.checklistDismissed"
    private const val KEY_ACCOUNTS_VISITED = "onboarding.accountsVisited"
    private const val KEY_TOUR_DONE = "onboarding.tourDone"
    private const val KEY_DISPLAY_NAME = "onboarding.displayName"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var hasCompletedWelcome: Boolean
        get() = prefs?.getBoolean(KEY_WELCOME_DONE, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_WELCOME_DONE, value)?.apply() }

    var isChecklistDismissed: Boolean
        get() = prefs?.getBoolean(KEY_CHECKLIST_DISMISSED, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_CHECKLIST_DISMISSED, value)?.apply() }

    var hasVisitedAccounts: Boolean
        get() = prefs?.getBoolean(KEY_ACCOUNTS_VISITED, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_ACCOUNTS_VISITED, value)?.apply() }

    var hasTakenTour: Boolean
        get() = prefs?.getBoolean(KEY_TOUR_DONE, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_TOUR_DONE, value)?.apply() }

    var userDisplayName: String
        get() = prefs?.getString(KEY_DISPLAY_NAME, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_DISPLAY_NAME, value)?.apply() }

    fun skipForExistingUser(transactionCount: Int, hasPlaidConnection: Boolean, isAuthenticated: Boolean) {
        if (hasCompletedWelcome) return
        if (transactionCount > 3 || hasPlaidConnection || isAuthenticated) {
            hasCompletedWelcome = true
            isChecklistDismissed = true
        }
    }
}
