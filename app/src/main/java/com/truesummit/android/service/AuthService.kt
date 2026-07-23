package com.truesummit.android.service

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Apple
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import java.security.MessageDigest

object AuthService {
    private val auth = SupabaseService.client.auth

    suspend fun signUp(email: String, password: String) {
        println("AuthService: Starting signUp for $email")
        try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            println("AuthService: signUp successful")
        } catch (e: Exception) {
            println("AuthService: signUp failed: ${e.message}")
            throw e
        }
    }

    suspend fun signIn(email: String, password: String) {
        println("AuthService: Starting signIn for $email")
        try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            println("AuthService: signIn successful")
        } catch (e: Exception) {
            println("AuthService: signIn failed: ${e.message}")
            throw e
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    suspend fun sendPasswordReset(email: String) {
        println("AuthService: Sending password reset to $email")
        try {
            auth.resetPasswordForEmail(email)
            println("AuthService: Password reset sent")
        } catch (e: Exception) {
            println("AuthService: Password reset failed: ${e.message}")
            throw e
        }
    }

    suspend fun signInWithApple(idToken: String, nonce: String) {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            this.nonce = nonce
            this.provider = Apple
        }
    }

    // linkIdentity only supports OAuth redirect flow in the Supabase Kotlin SDK;
    // linking an Apple ID token is not supported server-side via this path.
    suspend fun linkApple() {
        auth.linkIdentity(Apple)
    }

    fun randomNonceString(length: Int = 32): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._"
        return (1..length).map { charset.random() }.joinToString("")
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
