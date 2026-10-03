package dev.guilhermeluan.planner.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.session.LocalPlanner
import dev.guilhermeluan.planner.tasks.IdGenerator
import dev.guilhermeluan.planner.tasks.TaskDraft
import dev.guilhermeluan.planner.tasks.RoutineDraft
import dev.guilhermeluan.planner.tasks.RoutineOccurrenceStatus
import dev.guilhermeluan.planner.notifications.AndroidReminderAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class DayViewModel(
    private val dayPlanner: DayPlanner,
    private val initialDay: LocalDate = LocalDate.now(),
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

    fun bind(localPlanner: LocalPlanner) {
        if (accountId == localPlanner.account.id && plannerId == localPlanner.planner.id && observeJob?.isActive == true) {
            return
        }
        accountId = localPlanner.account.id
        plannerId = localPlanner.planner.id
        accountTimezone = localPlanner.account.timezone
        dayPlanner.bind(localPlanner.account, localPlanner.planner)
        selectedDay.value = LocalDate.now(ZoneId.of(accountTimezone))
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            launch {
                dayPlanner.rebuildReminders()
            }
            selectedDay.collectLatest { day ->
                _uiState.update { it.copy(selectedDay = day, isLoading = true) }
                coroutineScope {
                    launch {
                        dayPlanner.observeMarkedDays(Week.of(day)).collect { marked ->
                            _uiState.update { it.copy(markedDays = marked) }
                        }
                    }
                    dayPlanner.observeDay(day).collect { plan ->
                        _uiState.update { it.copy(selectedDay = day, plan = plan, isLoading = false) }
                    }
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

    fun updateTimezone(newTimezone: String) {
        accountTimezone = newTimezone
        accountId ?: return
        viewModelScope.launch {
            dayPlanner.updateTimezone(newTimezone)
        }
    }

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    fun createTask(draft: TaskDraft) {
        val account = accountId ?: return
        val planner = plannerId ?: return
        viewModelScope.launch {
            dayPlanner.createTask(draft.copy(day = selectedDay.value))
        }
    }

    fun toggleTask(taskId: String, completed: Boolean) {
        val account = accountId ?: return
        viewModelScope.launch {
            dayPlanner.setTaskCompleted(taskId, completed)
        }
    }

    fun createRoutine(draft: RoutineDraft) {
        val account = accountId ?: return
        val planner = plannerId ?: return
        viewModelScope.launch {
            dayPlanner.createRoutine(draft)
        }
    }

    fun restoreRoutine(routineId: String) {
        accountId ?: return
        viewModelScope.launch {
            dayPlanner.restoreRoutine(routineId)
        }
    }

    fun toggleRoutine(
        routineId: String,
        day: LocalDate,
        status: RoutineOccurrenceStatus,
    ) {
        val account = accountId ?: return
        viewModelScope.launch {
            dayPlanner.setRoutineOccurrenceStatus(routineId, day, status)
        }
    }

    fun editTask(taskId: String, title: String, time: java.time.LocalTime?) {
        val account = accountId ?: return
        viewModelScope.launch {
            dayPlanner.editTask(taskId, title, time)
        }
    }

    fun rescheduleTask(taskId: String, day: LocalDate) {
        val account = accountId ?: return
        viewModelScope.launch {
            dayPlanner.rescheduleTask(taskId, day)
        }
    }

    fun archiveTask(taskId: String) {
        val account = accountId ?: return
        viewModelScope.launch {
            dayPlanner.archiveTask(taskId)
        }
    }

    fun restoreTask(taskId: String) {
        val account = accountId ?: return
        viewModelScope.launch {
            dayPlanner.restoreTask(taskId)
        }
    }

    class Factory(private val application: PlannerApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val idGenerator = IdGenerator { UUID.randomUUID().toString() }
            return DayViewModel(
                dayPlanner = DayPlanner(
                    repository = dev.guilhermeluan.planner.tasks.RoomPlannerRepository(
                        database = application.database,
                        idGenerator = idGenerator,
                        clock = Clock.systemUTC(),
                    ),
                    reminders = AndroidReminderAdapter(application),
                ),
            ) as T
        }
    }
}
