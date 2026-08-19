package com.truesummit.android.ui.networth

import android.util.Log
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.truesummit.android.service.PlaidService
import com.truesummit.android.service.PlaidStorage
import com.truesummit.android.service.PlaidSyncService
import com.truesummit.android.service.StoredPlaidItem
import com.truesummit.android.service.SupabaseService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val TAG = "PlaidLink"

class PlaidConnectionsViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = PlaidStorage(application)
    private val syncService = PlaidSyncService(application)

    private val _items = MutableStateFlow<List<StoredPlaidItem>>(emptyList())
    val items: StateFlow<List<StoredPlaidItem>> = _items

    private val _syncingItemId = MutableStateFlow<String?>(null)
    val syncingItemId: StateFlow<String?> = _syncingItemId

    /** Non-null when a link token is ready to launch; reset to null after consumption. */
    private val _pendingLinkToken = MutableStateFlow<String?>(null)
    val pendingLinkToken: StateFlow<String?> = _pendingLinkToken

    /** Non-null when link token creation fails. */
    private val _linkError = MutableStateFlow<String?>(null)
    val linkError: StateFlow<String?> = _linkError

    private val _isLinkLoading = MutableStateFlow(false)
    val isLinkLoading: StateFlow<Boolean> = _isLinkLoading

    init {
        loadItems()
    }

    private fun loadItems() {
        _items.value = storage.getAllItems()
    }

    fun requestLink() {
        viewModelScope.launch {
            _isLinkLoading.value = true
            _linkError.value = null
            try {
                val userId = SupabaseService.currentUserID.first()?.toString() ?: "android_user"
                // "clientUserId", not "user_id": the Edge Function reads the
                // former, so the old key was discarded and every Android user
                // was created as the same Plaid user. "platform" selects the
                // native-SDK branch, which sends android_package_name instead
                // of the Hosted Link parameters iOS needs.
                val response = PlaidService.api.createLinkToken(
                    mapOf(
                        "clientUserId" to userId,
                        "platform" to "android"
                    )
                )
                Log.w(TAG, "link token created, launching Link")
                _pendingLinkToken.value = response.linkToken
            } catch (e: Exception) {
                Log.e(TAG, "link token request failed", e)
                _linkError.value = "Could not start bank link: ${e.localizedMessage}"
            } finally {
                _isLinkLoading.value = false
            }
        }
    }

    fun onLinkTokenConsumed() {
        _pendingLinkToken.value = null
    }

    fun onLinkSuccess(publicToken: String, institutionName: String?) {
        Log.w(TAG, "onLinkSuccess from ${institutionName ?: "unknown institution"}")
        viewModelScope.launch {
            try {
                // "publicToken", not "public_token": the Edge Function reads the
                // camelCase key. Plaid's own API is snake_case, which is what
                // made the wrong key look right — the exchange 400'd, the link
                // was discarded, and the bank appeared linked at Plaid while the
                // app showed nothing.
                val exchange = PlaidService.api.exchangePublicToken(mapOf("publicToken" to publicToken))
                val item = StoredPlaidItem(
                    itemId = exchange.itemId,
                    accessToken = exchange.accessToken,
                    institutionName = institutionName,
                    linkedAt = System.currentTimeMillis()
                )
                Log.w(TAG, "exchange ok, itemId=${exchange.itemId}; saving")
                storage.saveItem(item)
                Log.w(TAG, "item saved; stored count now ${storage.getAllItems().size}")
                loadItems()
                // Kick off an initial sync in the background
                syncItem(item)
            } catch (e: Exception) {
                // Retrofit's message is only "HTTP 400 Bad Request" — the Edge
                // Function's reason lives in the response body, which is the
                // one thing worth knowing when a link has already been spent.
                if (e is HttpException) {
                    Log.e(TAG, "exchange failed HTTP ${e.code()} body=${e.response()?.errorBody()?.string()}")
                }
                Log.e(TAG, "exchange/save failed", e)
                _linkError.value = "Bank linked but sync failed: ${e.localizedMessage}"
            }
        }
    }

    fun dismissLinkError() {
        _linkError.value = null
    }

    fun syncItem(item: StoredPlaidItem) {
        viewModelScope.launch {
            _syncingItemId.value = item.itemId
            try {
                Log.w(TAG, "sync starting for ${item.itemId}")
                syncService.syncAll(item)
                Log.w(TAG, "sync finished for ${item.itemId}")
            } catch (e: Exception) {
                // Sync errors are non-critical; item is already stored
                Log.e(TAG, "sync failed for ${item.itemId}", e)
            } finally {
                _syncingItemId.value = null
                loadItems()
            }
        }
    }

    fun unlinkItem(itemId: String) {
        storage.deleteItem(itemId)
        loadItems()
    }
}
