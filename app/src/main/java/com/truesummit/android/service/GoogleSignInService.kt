package com.truesummit.android.service

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.truesummit.android.BuildConfig

/**
 * The Credential Manager half of Sign in with Google. Produces an ID token,
 * hands it to [AuthService.signInWithGoogle], and lets Supabase mint the
 * session — the same shape as the Apple path on iOS.
 */
object GoogleSignInService {

    /** The user dismissed the account sheet. Not an error worth showing. */
    class Cancelled : Exception("Sign-in cancelled")

    suspend fun signIn(context: Context) {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        check(clientId.isNotEmpty()) {
            "GOOGLE_WEB_CLIENT_ID is missing from local.properties"
        }

        // Google is given the digest, Supabase the original. GoTrue hashes the
        // raw value itself before comparing it against the token's nonce claim,
        // so sending the digest to both ends fails verification.
        val rawNonce = AuthService.randomNonceString()
        val option = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(AuthService.sha256(rawNonce))
            .build()

        val response = try {
            CredentialManager.create(context).getCredential(
                context,
                GetCredentialRequest.Builder().addCredentialOption(option).build()
            )
        } catch (e: GetCredentialCancellationException) {
            throw Cancelled()
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Unexpected credential type: ${credential.type}")
        }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        AuthService.signInWithGoogle(googleCredential.idToken, rawNonce)
    }
}
