package dev.guilhermeluan.planner.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.guilhermeluan.planner.PlannerApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import dev.guilhermeluan.planner.sync.SyncScheduler
import dev.guilhermeluan.planner.tasks.IdGenerator
import java.time.Clock
import java.util.UUID

data class PlannerAppUiState(
    val configuration: ServerConfiguration? = null,
    val session: SessionState? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class PlannerViewModel(private val application: PlannerApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(
        PlannerAppUiState(configuration = application.serverConfigurationStore.read()),
    )
    val uiState: StateFlow<PlannerAppUiState> = _uiState.asStateFlow()

    private var repository: SessionRepository? = _uiState.value.configuration
        ?.let(application::sessionRepository)
    private val accountSettingsRepository = AccountSettingsRepository(
        database = application.database,
        idGenerator = IdGenerator { UUID.randomUUID().toString() },
        clock = Clock.systemUTC(),
    )

    init {
        val currentRepository = repository
        if (currentRepository != null) {
            _uiState.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                val session = currentRepository.restoreSession()
                _uiState.update { it.copy(session = session, isLoading = false) }
                if (session is SessionState.Ready) SyncScheduler.enqueue(application)
            }
        }
        viewModelScope.launch {
            application.observeBlockedReason().collect { reason ->
                if (reason != null && _uiState.value.session is SessionState.Ready) {
                    _uiState.update { it.copy(session = SessionState.Blocked(reason), isLoading = false) }
                }
            }
        }
    }

    fun configure(configuration: ServerConfiguration) {
        application.serverConfigurationStore.save(configuration)
        repository = application.sessionRepository(configuration)
        _uiState.value = PlannerAppUiState(
            configuration = configuration,
            session = SessionState.SignedOut,
        )
    }

    fun login(username: String, password: String) {
        val currentRepository = repository ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val session = runCatching { currentRepository.login(username, password) }
                .getOrElse { error -> sessionError(error) }
            _uiState.update {
                it.copy(
                    session = session,
                    isLoading = false,
                    error = (session as? SessionState.Error)?.message,
                )
            }
            if (session is SessionState.Ready) SyncScheduler.enqueue(application)
        }
    }

    fun changePassword(password: String) {
        val currentRepository = repository ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val session = runCatching { currentRepository.changePassword(password) }
                .getOrElse { error -> sessionError(error) }
            _uiState.update {
                it.copy(
                    session = session,
                    isLoading = false,
                    error = (session as? SessionState.Error)?.message,
                )
            }
            if (session is SessionState.Ready) SyncScheduler.enqueue(application)
        }
    }

    fun logout() {
        val currentRepository = repository ?: return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            currentRepository.logout()
            _uiState.update { it.copy(session = SessionState.SignedOut, isLoading = false) }
        }
    }

    fun updateTimezone(timezone: String) {
        val ready = _uiState.value.session as? SessionState.Ready ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { accountSettingsRepository.updateTimezone(ready.account.id, timezone) }
                .onSuccess {
                    val updatedAccount = ready.account.copy(timezone = timezone)
                    _uiState.update {
                        it.copy(
                            session = ready.copy(account = updatedAccount),
                            isLoading = false,
                            error = null,
                        )
                    }
                    SyncScheduler.enqueue(application)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Fuso da Conta inválido")
                    }
                }
        }
    }

    fun changeServer() {
        val currentRepository = repository
        application.serverConfigurationStore.clear()
        repository = null
        _uiState.value = PlannerAppUiState()
        if (currentRepository != null) {
            viewModelScope.launch { runCatching { currentRepository.logout() } }
        }
    }

    fun resumeAfterBlock() {
        viewModelScope.launch {
            application.clearSessionBlocked()
            _uiState.update { it.copy(session = SessionState.SignedOut, isLoading = false, error = null) }
        }
    }

    private fun sessionError(error: Throwable): SessionState.Error = when (error) {
        is SessionApiException -> SessionState.Error(
            code = error.code ?: "server_error",
            message = error.message,
        )
        is IOException -> SessionState.Error(
            code = "server_unreachable",
            message = "Não foi possível alcançar o servidor. Confira a rede e tente novamente.",
        )
        else -> SessionState.Error(
            code = "unexpected_error",
            message = "Algo não saiu como esperado. Tente novamente.",
        )
    }

    class Factory(private val application: PlannerApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlannerViewModel(application) as T
    }
}
