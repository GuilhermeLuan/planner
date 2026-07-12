package dev.guilhermeluan.planner.session

import kotlinx.coroutines.flow.Flow

data class Account(
    val id: String,
    val username: String,
    val timezone: String,
    val mustChangePassword: Boolean,
)

data class Planner(
    val id: String,
    val accountId: String,
)

data class LoginPayload(
    val token: String,
    val account: Account,
    val planner: Planner,
)

sealed interface SessionState {
    data object SignedOut : SessionState
    data class PasswordChangeRequired(val account: Account) : SessionState
    data class Ready(val account: Account, val planner: Planner) : SessionState
    data class Blocked(val reason: String) : SessionState
    data class Error(val code: String, val message: String) : SessionState
}

interface SessionApi {
    suspend fun login(username: String, password: String): LoginPayload
    suspend fun changePassword(token: String, password: String)
    suspend fun logout(token: String)
}

interface SessionStateStore {
    suspend fun saveActive(account: Account, planner: Planner)
    suspend fun readActive(): Pair<Account, Planner>?
    suspend fun clearActiveAccess()
    suspend fun markBlocked(reason: String) {}
    suspend fun blockedReason(): String? = null
    suspend fun clearBlocked() {}
    fun observeBlockedReason(): Flow<String?>? = null
}

interface SessionSecretStore {
    suspend fun writeToken(token: String)
    suspend fun readToken(): String?
    suspend fun clearToken()
}

class InMemorySessionStateStore : SessionStateStore {
    private var active: Pair<Account, Planner>? = null
    private var blocked: String? = null

    override suspend fun saveActive(account: Account, planner: Planner) {
        active = account to planner
    }

    override suspend fun readActive(): Pair<Account, Planner>? = active

    override suspend fun clearActiveAccess() {
        active = null
    }

    override suspend fun markBlocked(reason: String) {
        blocked = reason
    }

    override suspend fun blockedReason(): String? = blocked

    override suspend fun clearBlocked() {
        blocked = null
    }
}

class InMemorySessionSecretStore : SessionSecretStore {
    private var token: String? = null

    override suspend fun writeToken(token: String) {
        this.token = token
    }

    override suspend fun readToken(): String? = token

    override suspend fun clearToken() {
        token = null
    }
}
