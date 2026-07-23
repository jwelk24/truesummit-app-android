package com.truesummit.android.service

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
data class StoredPlaidItem(
    val itemId: String,
    val accessToken: String,
    val institutionName: String?,
    val linkedAt: Long
)

class PlaidStorage(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        "plaid_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun saveItem(item: StoredPlaidItem) {
        val jsonString = json.encodeToString(item)
        sharedPreferences.edit().putString("item_${item.itemId}", jsonString).apply()
    }

    fun getAllItems(): List<StoredPlaidItem> {
        return sharedPreferences.all.filterKeys { it.startsWith("item_") }
            .values.mapNotNull { 
                try {
                    json.decodeFromString<StoredPlaidItem>(it as String)
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.linkedAt }
    }

    fun deleteItem(itemId: String) {
        sharedPreferences.edit().remove("item_$itemId").remove("cursor_$itemId").apply()
    }

    fun getCursor(itemId: String): String? {
        return sharedPreferences.getString("cursor_$itemId", null)
    }

    fun setCursor(itemId: String, cursor: String) {
        sharedPreferences.edit().putString("cursor_$itemId", cursor).apply()
    }
}
