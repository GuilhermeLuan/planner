package dev.guilhermeluan.planner.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun `valid connected login bootstraps a session that opens offline`() = runTest {
        val account = Account(
            id = "account-1",
            username = "gui",
            timezone = "America/Sao_Paulo",
            mustChangePassword = false,
        )
        val planner = Planner(id = "planner-1", accountId = account.id)
        val localState = InMemorySessionStateStore()
        val secrets = InMemorySessionSecretStore()
        val connectedRepository = SessionRepository(
            api = StubSessionApi(LoginPayload("token-1", account, planner)),
            stateStore = localState,
            secretStore = secrets,
        )

        val login = connectedRepository.login("gui", "correct-password")
        val offlineRepository = SessionRepository(
            api = OfflineSessionApi,
            stateStore = localState,
            secretStore = secrets,
        )

        assertTrue(login is SessionState.Ready)
        assertEquals(SessionState.Ready(account, planner), offlineRepository.restoreSession())
    }

    @Test
    fun `temporary password keeps the session gated until a successful change`() = runTest {
        val account = Account(
            id = "account-1",
            username = "gui",
            timezone = "America/Sao_Paulo",
            mustChangePassword = true,
        )
        val planner = Planner(id = "planner-1", accountId = account.id)
        val repository = SessionRepository(
            api = StubSessionApi(LoginPayload("token-1", account, planner)),
            stateStore = InMemorySessionStateStore(),
            secretStore = InMemorySessionSecretStore(),
        )

        val login = repository.login("gui", "temporary-password")
        val changed = repository.changePassword("new-private-password")

        assertEquals(SessionState.PasswordChangeRequired(account), login)
        assertEquals(SessionState.Ready(account.copy(mustChangePassword = false), planner), changed)
    }

    @Test
    fun `logout removes local access so another account can sign in without mixing planners`() = runTest {
        val first = Account("account-1", "gui", "America/Sao_Paulo", false)
        val firstPlanner = Planner("planner-1", first.id)
        val localState = InMemorySessionStateStore()
        val secrets = InMemorySessionSecretStore()
        val firstRepository = SessionRepository(
            api = StubSessionApi(LoginPayload("token-1", first, firstPlanner)),
            stateStore = localState,
            secretStore = secrets,
        )
        firstRepository.login("gui", "correct-password")

        assertEquals(SessionState.SignedOut, firstRepository.logout())
        assertEquals(SessionState.SignedOut, firstRepository.restoreSession())

        val second = Account("account-2", "ana", "America/Fortaleza", false)
        val secondPlanner = Planner("planner-2", second.id)
        val secondRepository = SessionRepository(
            api = StubSessionApi(LoginPayload("token-2", second, secondPlanner)),
            stateStore = localState,
            secretStore = secrets,
        )
        secondRepository.login("ana", "another-password")

        assertEquals(SessionState.Ready(second, secondPlanner), secondRepository.restoreSession())
    }

    @Test
    fun `invalid credentials return an actionable error without creating a local session`() = runTest {
        val repository = SessionRepository(
            api = RejectingSessionApi(SessionApiException(401, null, "invalid credentials")),
            stateStore = InMemorySessionStateStore(),
            secretStore = InMemorySessionSecretStore(),
        )

        val result = repository.login("gui", "wrong-password")

        assertEquals(
            SessionState.Error("invalid_credentials", "Usuário ou senha incorretos."),
            result,
        )
        assertEquals(SessionState.SignedOut, repository.restoreSession())
    }

    @Test
    fun `disabled account explains that access must be restored by the administrator`() = runTest {
        val repository = SessionRepository(
            api = RejectingSessionApi(
                SessionApiException(403, "account_disabled", "account is disabled"),
            ),
            stateStore = InMemorySessionStateStore(),
            secretStore = InMemorySessionSecretStore(),
        )

        val result = repository.login("gui", "correct-password")

        assertEquals(
            SessionState.Error(
                "account_disabled",
                "Esta Conta está desativada. Peça a reativação à Conta administradora.",
            ),
            result,
        )
    }

    @Test
    fun `revocation blocks an offline restore without deleting the local identity`() = runTest {
        val account = Account("account-1", "gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        val state = InMemorySessionStateStore()
        val secrets = InMemorySessionSecretStore()
        val repository = SessionRepository(
            api = StubSessionApi(LoginPayload("token-1", account, planner)),
            stateStore = state,
            secretStore = secrets,
        )
        repository.login("gui", "password")
        state.markBlocked("Conta desativada")
        secrets.clearToken()

        assertEquals(SessionState.Blocked("Conta desativada"), repository.restoreSession())
        assertEquals(account to planner, state.readActive())
    }

    @Test
    fun `an unreadable protected token returns the account to login without clearing local identity`() = runTest {
        val account = Account("account-1", "gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        val state = InMemorySessionStateStore().apply { saveActive(account, planner) }
        val secrets = UnreadableSessionSecretStore()
        val repository = SessionRepository(
            api = OfflineSessionApi,
            stateStore = state,
            secretStore = secrets,
        )

        assertEquals(SessionState.SignedOut, repository.restoreSession())
        assertTrue(secrets.wasCleared)
        assertEquals(account to planner, state.readActive())
    }
}

private class StubSessionApi(private val payload: LoginPayload) : SessionApi {
    override suspend fun login(username: String, password: String): LoginPayload = payload
    override suspend fun changePassword(token: String, password: String) = Unit
    override suspend fun logout(token: String) = Unit
}

private object OfflineSessionApi : SessionApi {
    override suspend fun login(username: String, password: String): LoginPayload = error("offline")
    override suspend fun changePassword(token: String, password: String) = error("offline")
    override suspend fun logout(token: String) = error("offline")
}

private class RejectingSessionApi(private val error: Throwable) : SessionApi {
    override suspend fun login(username: String, password: String): LoginPayload = throw error
    override suspend fun changePassword(token: String, password: String) = throw error
    override suspend fun logout(token: String) = throw error
}

private class UnreadableSessionSecretStore : SessionSecretStore {
    var wasCleared = false

    override suspend fun writeToken(token: String) = Unit

    override suspend fun readToken(): String = throw IllegalStateException("Android Keystore unavailable")

    override suspend fun clearToken() {
        wasCleared = true
    }
}
