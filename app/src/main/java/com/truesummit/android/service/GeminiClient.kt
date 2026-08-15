package com.truesummit.android.service

import android.util.Log
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One turn of a conversation. [isUser] false means the model spoke. */
data class AiTurn(val text: String, val isUser: Boolean)

class AiUnavailable(message: String) : Exception(message)

/**
 * Talks to the `ai` Supabase Edge Function.
 *
 * The API key stays server-side — shipping it in the APK would let anyone
 * unpack the app and spend the quota, subscription or not. The function is
 * declared verify_jwt = true, so Supabase rejects unauthenticated callers
 * before the function runs at all.
 */
object GeminiClient {

    private const val TAG = "GeminiClient"
    private val ENDPOINT = "${SupabaseService.FUNCTIONS_URL}/ai"
    private const val TIMEOUT_MS = 60_000

    /**
     * Single-shot prompt. Returns null if the call fails for any reason.
     *
     * Callers treat null as "no result" and show nothing, so without this log
     * a broken key, an expired session or a retired model all look identical
     * to the AI simply having nothing to say.
     */
    suspend fun generate(prompt: String): String? =
        runCatching { generateOrThrow(listOf(AiTurn(prompt, isUser = true))) }
            .onFailure { Log.w(TAG, "AI request failed: ${it.message}") }
            .getOrNull()

    /** Multi-turn variant; surfaces failures so chat can show a real message. */
    suspend fun chat(history: List<AiTurn>): String = generateOrThrow(history)

    private suspend fun generateOrThrow(turns: List<AiTurn>): String = withContext(Dispatchers.IO) {
        val token = SupabaseService.client.auth.currentAccessTokenOrNull()
            ?: throw AiUnavailable("Sign in to use AI features.")

        val contents = JSONArray().apply {
            turns.forEach { turn ->
                put(JSONObject().apply {
                    put("role", if (turn.isUser) "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
                })
            }
        }
        val body = JSONObject()
            .put("model", "gemini-1.5-flash")
            .put("contents", contents)
            .toString()

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }

        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val payload = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(payload).getJSONObject("error").optString("message")
                }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "AI request failed ($code)."
                // Log the raw payload: the user-facing message is deliberately
                // vague, which makes a misconfigured key or a retired model
                // indistinguishable from the model having nothing to say.
                Log.w(TAG, "AI HTTP $code: ${payload.take(400)}")
                throw AiUnavailable(message)
            }

            JSONObject(payload).optString("text")
        } finally {
            conn.disconnect()
        }
    }
}
