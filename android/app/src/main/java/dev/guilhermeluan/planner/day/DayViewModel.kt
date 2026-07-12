package dev.guilhermeluan.planner.day

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.session.SessionState
import dev.guilhermeluan.planner.tasks.IdGenerator
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.tasks.TaskDraft
import dev.guilhermeluan.planner.tasks.RoutineDraft
import dev.guilhermeluan.planner.tasks.RoutineOccurrenceStatus
import dev.guilhermeluan.planner.notifications.PlannerNotificationScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class DayViewModel(
    private val repository: RoomPlannerRepository,
    private val initialDay: LocalDate = LocalDate.now(),
    private val syncContext: Context? = null,
) : ViewModel() {
    private val selectedDay = MutableStateFlow(initialDay)
    private val _uiState = MutableStateFlow(
        DayUiState(
            selectedDay = initialDay,
            plan = dev.guilhermeluan.planner.tasks.DayPlan(initialDay, emptyList(), emptyList()),
        ),
    )
    val uiState: StateFlow<DayUiState> = _uiState.asStateFlow()

    private var accountId: String? = null
    private var plannerId: String? = null
    private var accountTimezone: String = java.time.ZoneId.systemDefault().id
    private var observeJob: Job? = null

    fun bind(session: SessionState.Ready) {
        if (accountId == session.account.id && plannerId == session.planner.id && observeJob?.isActive == true) {
            return
        }
        accountId = session.account.id
        plannerId = session.planner.id
        accountTimezone = session.account.timezone
        selectedDay.value = LocalDate.now(ZoneId.of(accountTimezone))
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            launch {
                repository.observeScheduledTasks(session.account.id).collect { tasks ->
                    syncContext?.let {
                        PlannerNotificationScheduler.rebuild(it, tasks, accountTimezone)
                    }
                }
            }
            selectedDay.collectLatest { day ->
                _uiState.update { it.copy(selectedDay = day, isLoading = true, syncError = null) }
                repository.observeDay(session.account.id, day).collect { plan ->
                    _uiState.update { it.copy(selectedDay = day, plan = plan, isLoading = false) }
                }
            }
        }
    }

    fun unbind() {
        accountId = null
        plannerId = null
        observeJob?.cancel()
        observeJob = null
    }

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    fun createTask(draft: TaskDraft) {
        val account = accountId ?: return
        val planner = plannerId ?: return
        viewModelScope.launch {
            val task = repository.createTask(
                accountId = account,
                plannerId = planner,
                draft = draft.copy(day = selectedDay.value),
            )
            PlannerNotificationScheduler.scheduleTask(syncContext ?: return@launch, task, accountTimezone)
        }
    }

    fun toggleTask(taskId: String, completed: Boolean) {
        val account = accountId ?: return
        viewModelScope.launch {
            val task = repository.setTaskCompleted(account, taskId, completed)
            if (completed) {
                syncContext?.let { PlannerNotificationScheduler.cancelTask(it, taskId) }
            } else {
                PlannerNotificationScheduler.scheduleTask(syncContext ?: return@launch, task, accountTimezone)
            }
        }
    }

    fun createRoutine(draft: RoutineDraft) {
        val account = accountId ?: return
        val planner = plannerId ?: return
        viewModelScope.launch {
            repository.createRoutine(account, planner, draft)
        }
    }

    fun toggleRoutine(
        routineId: String,
        day: LocalDate,
        status: RoutineOccurrenceStatus,
    ) {
        val account = accountId ?: return
        viewModelScope.launch {
            repository.setRoutineOccurrenceStatus(account, routineId, day, status)
        }
    }

    fun editTask(taskId: String, title: String, time: java.time.LocalTime?) {
        val account = accountId ?: return
        viewModelScope.launch {
            val task = repository.editTask(account, taskId, title, time)
            PlannerNotificationScheduler.scheduleTask(syncContext ?: return@launch, task, accountTimezone)
        }
    }

    fun rescheduleTask(taskId: String, day: LocalDate) {
        val account = accountId ?: return
        viewModelScope.launch {
            val task = repository.rescheduleTask(account, taskId, day)
            PlannerNotificationScheduler.scheduleTask(syncContext ?: return@launch, task, accountTimezone)
        }
    }

    fun archiveTask(taskId: String) {
        val account = accountId ?: return
        viewModelScope.launch {
            repository.archiveTask(account, taskId)
            syncContext?.let { PlannerNotificationScheduler.cancelTask(it, taskId) }
        }
    }

    fun restoreTask(taskId: String) {
        val account = accountId ?: return
        viewModelScope.launch {
            val task = repository.restoreTask(account, taskId)
            PlannerNotificationScheduler.scheduleTask(syncContext ?: return@launch, task, accountTimezone)
        }
    }

    class Factory(private val application: PlannerApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val idGenerator = IdGenerator { UUID.randomUUID().toString() }
            return DayViewModel(
                repository = RoomPlannerRepository(
                    database = application.database,
                    idGenerator = idGenerator,
                    clock = Clock.systemUTC(),
                ),
                syncContext = application,
            ) as T
        }
    }
}
