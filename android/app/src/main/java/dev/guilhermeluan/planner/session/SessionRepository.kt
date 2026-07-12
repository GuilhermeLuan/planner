package dev.guilhermeluan.planner.session

import kotlinx.coroutines.CancellationException

class SessionRepository(
    private val api: SessionApi,
    private val stateStore: SessionStateStore,
    private val secretStore: SessionSecretStore,
) {
    suspend fun login(username: String, password: String): SessionState {
        val payload = try {
            api.login(username, password)
        } catch (error: SessionApiException) {
            if (error.status == 401) {
                return SessionState.Error(
                    code = "invalid_credentials",
                    message = "Usuário ou senha incorretos.",
                )
            }
            if (error.code == "account_disabled") {
                return SessionState.Error(
                    code = "account_disabled",
                    message = "Esta Conta está desativada. Peça a reativação à Conta administradora.",
                )
            }
            throw error
        }
        stateStore.clearBlocked()
        stateStore.saveActive(payload.account, payload.planner)
        secretStore.writeToken(payload.token)
        return if (payload.account.mustChangePassword) {
            SessionState.PasswordChangeRequired(payload.account)
        } else {
            SessionState.Ready(payload.account, payload.planner)
        }
    }

    suspend fun restoreSession(): SessionState {
        stateStore.blockedReason()?.let { return SessionState.Blocked(it) }
        val token = try {
            secretStore.readToken()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            try {
                secretStore.clearToken()
            } catch (clearError: Throwable) {
                if (clearError is CancellationException) throw clearError
            }
            return SessionState.SignedOut
        }
        if (token == null) return SessionState.SignedOut
        val (account, planner) = stateStore.readActive() ?: return SessionState.SignedOut
        return if (account.mustChangePassword) {
            SessionState.PasswordChangeRequired(account)
        } else {
            SessionState.Ready(account, planner)
        }
    }

    suspend fun changePassword(password: String): SessionState {
        val token = secretStore.readToken() ?: return SessionState.SignedOut
        val (account, planner) = stateStore.readActive() ?: return SessionState.SignedOut
        api.changePassword(token, password)
        val updatedAccount = account.copy(mustChangePassword = false)
        stateStore.clearBlocked()
        stateStore.saveActive(updatedAccount, planner)
        return SessionState.Ready(updatedAccount, planner)
    }

    suspend fun logout(): SessionState {
        val token = secretStore.readToken()
        try {
            if (token != null) api.logout(token)
        } finally {
            secretStore.clearToken()
            stateStore.clearActiveAccess()
            stateStore.clearBlocked()
        }
        return SessionState.SignedOut
    }
}
