package com.truesummit.android.service

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID

object SupabaseService {
    private const val PROJECT_URL = "https://eebpmgilbguussctttgl.supabase.co"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVlYnBtZ2lsYmd1dXNzY3R0dGdsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE5MTY1MjEsImV4cCI6MjA5NzQ5MjUyMX0.D7agtESCefKpfAhuMq-x40Xlj7hWfp5oInE-ophrEWg"

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = PROJECT_URL,
        supabaseKey = ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

    private val _currentUserID = MutableStateFlow<UUID?>(null)
    val currentUserID: StateFlow<UUID?> = _currentUserID

    private val _currentEmail = MutableStateFlow<String?>(null)
    val currentEmail: StateFlow<String?> = _currentEmail

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val scope = CoroutineScope(Dispatchers.Main)

    suspend fun loadUser() {
        try {
            val user = client.auth.retrieveUserForCurrentSession()
            _currentUserID.value = user.id?.let { UUID.fromString(it) }
            _currentEmail.value = user.email
            _isAuthenticated.value = true
        } catch (_: Exception) {
            // No session — leave state as-is.
        }
    }

    init {
        client.auth.sessionStatus
            .onEach { status ->
                println("SupabaseService: Auth status changed to $status")
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        _currentUserID.value = user?.id?.let { UUID.fromString(it) }
                        _currentEmail.value = user?.email
                        _isAuthenticated.value = true
                        println("SupabaseService: User authenticated: ${user?.email}")
                    }
                    else -> {
                        _currentUserID.value = null
                        _currentEmail.value = null
                        _isAuthenticated.value = false
                        println("SupabaseService: User not authenticated")
                    }
                }
            }
            .launchIn(scope)
    }
}
