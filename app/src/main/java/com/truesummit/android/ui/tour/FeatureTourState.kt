package com.truesummit.android.ui.tour

import com.truesummit.android.ui.onboarding.OnboardingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object FeatureTourState {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _currentStop = MutableStateFlow(0)
    val currentStop: StateFlow<Int> = _currentStop

    fun start() {
        _currentStop.value = 0
        _isActive.value = true
    }

    fun advance(index: Int) {
        _currentStop.value = index
    }

    fun finish() {
        _isActive.value = false
        OnboardingState.hasTakenTour = true
    }

    fun close() {
        _isActive.value = false
    }
}
