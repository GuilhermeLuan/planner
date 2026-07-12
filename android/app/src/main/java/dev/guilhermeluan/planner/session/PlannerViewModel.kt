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
import java.time.ZoneId

sealed interface PlannerAppUiState {
    data object Loading : PlannerAppUiState
    data class NeedsOnboarding(
        val suggestedTimezone: String,
        val isSaving: Boolean = false,
        val error: String? = null,
    ) : PlannerAppUiState
    data class Ready(val localPlanner: LocalPlanner) : PlannerAppUiState
}

class PlannerViewModel(
    private val repository: LocalPlannerRepository,
    private val migration: MigrationToLocal,
    private val settingsRepository: AccountSettingsRepository,
    private val deviceTimezone: () -> String = { ZoneId.systemDefault().id },
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlannerAppUiState>(PlannerAppUiState.Loading)
    val uiState: StateFlow<PlannerAppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { migration.runIfNeeded() }
            _uiState.value = repository.restorePlanner()
                ?.let(PlannerAppUiState::Ready)
                ?: PlannerAppUiState.NeedsOnboarding(deviceTimezone())
        }
    }

    fun createPlanner(name: String) {
        val onboarding = _uiState.value as? PlannerAppUiState.NeedsOnboarding ?: return
        _uiState.value = onboarding.copy(isSaving = true, error = null)
        viewModelScope.launch {
            runCatching { repository.createPlanner(name, onboarding.suggestedTimezone) }
                .onSuccess { _uiState.value = PlannerAppUiState.Ready(it) }
                .onFailure { error ->
                    _uiState.update {
                        onboarding.copy(error = error.message ?: "Não foi possível criar seu Planner")
                    }
                }
        }
    }

    fun saveTimezone(timezone: String) {
        val localPlanner = (_uiState.value as? PlannerAppUiState.Ready)?.localPlanner ?: return
        viewModelScope.launch {
            runCatching {
                settingsRepository.updateTimezone(localPlanner.account.id, timezone)
                _uiState.update { state ->
                    val ready = state as? PlannerAppUiState.Ready ?: return@update state
                    val updated = ready.localPlanner.account.copy(timezone = timezone)
                    PlannerAppUiState.Ready(ready.localPlanner.copy(account = updated))
                }
            }
        }
    }

    class Factory(private val application: PlannerApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlannerViewModel(
                application.localPlannerRepository,
                application.migrationToLocal,
                application.accountSettingsRepository,
            ) as T
    }
}
