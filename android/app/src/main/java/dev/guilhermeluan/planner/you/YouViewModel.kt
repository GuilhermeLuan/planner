package dev.guilhermeluan.planner.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.backup.BackupExporter
import dev.guilhermeluan.planner.session.LocalPlanner
import dev.guilhermeluan.planner.tasks.ArchivedItems
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

class YouViewModel(
    private val consistency: ConsistencyRepository,
    private val planner: RoomPlannerRepository,
    private val backup: BackupExporter,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        YouUiState(
            name = "", timezone = ZoneId.systemDefault().id, memberSince = null,
            today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
            consistency = null, notificationsEnabled = true, lastBackup = null, archived = ArchivedItems(),
        ),
    )
    val uiState: StateFlow<YouUiState> = _uiState.asStateFlow()

    private var bound: LocalPlanner? = null
    private var observeJob: Job? = null

    /** Segue o nome e o Fuso da Conta: mudar qualquer um reabre a observação, que vira de mês pelo fuso novo. */
    fun bind(localPlanner: LocalPlanner) {
        if (bound == localPlanner && observeJob?.isActive == true) return
        bound = localPlanner
        val account = localPlanner.account
        val zone = ZoneId.of(account.timezone)
        _uiState.update {
            it.copy(name = account.username, timezone = account.timezone, today = clock.instant().atZone(zone).toLocalDate())
        }
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val memberSince = consistency.memberSince(account.id, zone)
            _uiState.update { it.copy(memberSince = memberSince) }
            combine(
                consistency.observe(account.id, zone),
                planner.observeArchivedItems(account.id),
                backup.observeLastSaved(),
            ) { numbers, archived, lastBackup ->
                Triple(numbers, archived, lastBackup?.atZone(zone)?.toLocalDate())
            }.collect { (numbers, archived, lastBackup) ->
                _uiState.update { it.copy(consistency = numbers, archived = archived, lastBackup = lastBackup) }
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) = _uiState.update { it.copy(notificationsEnabled = enabled) }

    /** O arquivo do backup foi gravado: guarda o momento para "Último em ...". */
    fun backupSaved() {
        viewModelScope.launch { backup.recordSaved() }
    }

    class Factory(private val application: PlannerApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            YouViewModel(application.consistencyRepository, application.medicinesRepository, application.backupExporter) as T
    }
}
