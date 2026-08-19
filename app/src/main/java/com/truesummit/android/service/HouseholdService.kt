package com.truesummit.android.service

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.truesummit.android.data.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

enum class HouseholdRole(val value: String) {
    OWNER("owner"),
    MEMBER("member"),
    VIEWER("viewer");

    val canWrite: Boolean get() = this == OWNER || this == MEMBER
    val canInvite: Boolean get() = this == OWNER
}

@Serializable
data class Household(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String,
    @Serializable(with = UUIDSerializer::class) val owner_user_id: UUID,
    val created_at: String
)

// No id: household_members is keyed by (household_id, user_id) and has no id
// column, so requiring one made every decode throw and left the household
// state permanently empty.
@Serializable
data class HouseholdMember(
    @Serializable(with = UUIDSerializer::class) val household_id: UUID,
    @Serializable(with = UUIDSerializer::class) val user_id: UUID,
    val role: String,
    val joined_at: String
)

enum class HouseholdError(val message: String) {
    NOT_AUTHENTICATED("Sign in first."),
    NO_HOUSEHOLD("No household available."),
    NOT_OWNER("Only the household owner can create invites."),
    INVALID_CODE("Enter a valid invite code.")
}

@Serializable
private data class InviteInsert(
    val code: String,
    @Serializable(with = UUIDSerializer::class) val household_id: UUID,
    val role: String,
    @Serializable(with = UUIDSerializer::class) val created_by: UUID,
    val expires_at: String
)

@Serializable
private data class RedeemParams(val invite_code: String)

object HouseholdService {
    private val _currentHousehold = MutableStateFlow<Household?>(null)
    val currentHousehold: StateFlow<Household?> = _currentHousehold

    private val _currentRole = MutableStateFlow<HouseholdRole?>(null)
    val currentRole: StateFlow<HouseholdRole?> = _currentRole

    private val _members = MutableStateFlow<List<HouseholdMember>>(emptyList())
    val members: StateFlow<List<HouseholdMember>> = _members

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    suspend fun refresh() {
        val userIDStr = SupabaseService.client.auth.currentUserOrNull()?.id
        if (userIDStr != null) {
            loadHousehold(UUID.fromString(userIDStr))
        } else {
            _currentHousehold.value = null
            _currentRole.value = null
            _members.value = emptyList()
        }
    }

    suspend fun loadHousehold(userID: UUID) {
        _isLoading.value = true
        try {
            val response = SupabaseService.client.postgrest["household_members"].select {
                filter {
                    filter("user_id", FilterOperator.EQ, userID.toString())
                }
            }

            val member = response.decodeList<HouseholdMember>().firstOrNull()
            if (member == null) {
                _currentHousehold.value = null
                _currentRole.value = null
                _members.value = emptyList()
            } else {
                val hResponse = SupabaseService.client.postgrest["households"].select {
                    filter {
                        filter("id", FilterOperator.EQ, member.household_id.toString())
                    }
                }
                _currentHousehold.value = hResponse.decodeList<Household>().firstOrNull()
                _currentRole.value = when (member.role) {
                    "owner" -> HouseholdRole.OWNER
                    "viewer" -> HouseholdRole.VIEWER
                    else -> HouseholdRole.MEMBER
                }
                loadMembers(member.household_id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun loadMembers(householdID: UUID) {
        try {
            val response = SupabaseService.client.postgrest["household_members"].select {
                filter {
                    filter("household_id", FilterOperator.EQ, householdID.toString())
                }
            }
            _members.value = response.decodeList<HouseholdMember>()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createInvite(role: HouseholdRole = HouseholdRole.MEMBER, expiresInDays: Int = 7): String {
        val household = _currentHousehold.value ?: throw IllegalStateException(HouseholdError.NO_HOUSEHOLD.message)
        val userIDStr = SupabaseService.client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException(HouseholdError.NOT_AUTHENTICATED.message)
        if (_currentRole.value?.canInvite != true) throw IllegalStateException(HouseholdError.NOT_OWNER.message)

        val code = generateInviteCode()
        val expiresAt = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, expiresInDays)
        }.time
        val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val payload = InviteInsert(
            code = code,
            household_id = household.id,
            role = role.value,
            created_by = UUID.fromString(userIDStr),
            expires_at = df.format(expiresAt)
        )
        SupabaseService.client.postgrest["household_invites"].insert(payload)
        return code
    }

    suspend fun redeemInvite(code: String) {
        val trimmed = code.trim().uppercase()
        if (trimmed.isEmpty()) throw IllegalArgumentException(HouseholdError.INVALID_CODE.message)
        SupabaseService.client.postgrest.rpc("redeem_household_invite", RedeemParams(trimmed))
        refresh()
    }

    private fun generateInviteCode(): String {
        // Crockford base32 alphabet — no ambiguous chars (0/O, 1/I/L removed)
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toList()
        return (0 until 8).map { alphabet.random() }.joinToString("")
    }
}
