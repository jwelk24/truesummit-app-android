package com.truesummit.android.service

import com.truesummit.android.BuildConfig
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
 * Talks to the backend's Gemini proxy.
 *
 * The API key stays on the server — shipping it in the APK would let anyone
 * unpack the app and spend the quota, subscription or not. Requests carry the
 * caller's Supabase token so the endpoint is not open to the world.
 */
object GeminiClient {

    private const val ENDPOINT = "/api/ai/generate"
    private const val TIMEOUT_MS = 60_000

    /** Single-shot prompt. Returns null if the call fails for any reason. */
    suspend fun generate(prompt: String): String? =
        runCatching { generateOrThrow(listOf(AiTurn(prompt, isUser = true))) }.getOrNull()

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

        val conn = (URL(BuildConfig.BACKEND_URL + ENDPOINT).openConnection() as HttpURLConnection).apply {
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
                throw AiUnavailable(message)
            }

            JSONObject(payload).optString("text")
        } finally {
            conn.disconnect()
        }
    }
}
