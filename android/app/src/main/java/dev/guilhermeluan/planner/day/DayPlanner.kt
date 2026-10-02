package dev.guilhermeluan.planner.day

import dev.guilhermeluan.planner.notifications.ReminderAdapter
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.tasks.DayPlan
import dev.guilhermeluan.planner.tasks.PlannedRoutineOccurrence
import dev.guilhermeluan.planner.tasks.PlannerRoutine
import dev.guilhermeluan.planner.tasks.PlannerTask
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.tasks.RoutineDraft
import dev.guilhermeluan.planner.tasks.RoutineOccurrenceStatus
import dev.guilhermeluan.planner.tasks.TaskDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

class DayPlanner(
    private val repository: RoomPlannerRepository,
    private val reminders: ReminderAdapter,
) {
    private lateinit var account: Account
    private lateinit var planner: Planner

    fun bind(account: Account, planner: Planner) {
        this.account = account
        this.planner = planner
    }

    fun observeDay(day: LocalDate): Flow<DayPlan> = repository.observeDay(account.id, day)

    fun observeMarkedDays(week: ClosedRange<LocalDate>): Flow<Set<LocalDate>> =
        repository.observeMarkedDays(account.id, week)

    suspend fun rebuildReminders() =
        reminders.rebuild(repository.observeScheduledTasks(account.id).first(), account.timezone)

    suspend fun updateTimezone(timezone: String) {
        account = account.copy(timezone = timezone)
        rebuildReminders()
    }

    suspend fun createTask(draft: TaskDraft): PlannerTask =
        repository.createTask(account.id, planner.id, draft).also(::reconcile)

    suspend fun setTaskCompleted(taskId: String, completed: Boolean): PlannerTask =
        repository.setTaskCompleted(account.id, taskId, completed).also(::reconcile)

    suspend fun editTask(taskId: String, title: String, time: LocalTime?): PlannerTask =
        repository.editTask(account.id, taskId, title, time).also(::reconcile)

    suspend fun rescheduleTask(taskId: String, day: LocalDate): PlannerTask =
        repository.rescheduleTask(account.id, taskId, day).also(::reconcile)

    suspend fun archiveTask(taskId: String): PlannerTask =
        repository.archiveTask(account.id, taskId).also(::reconcile)

    suspend fun restoreTask(taskId: String): PlannerTask =
        repository.restoreTask(account.id, taskId).also(::reconcile)

    suspend fun createRoutine(draft: RoutineDraft): PlannerRoutine =
        repository.createRoutine(account.id, planner.id, draft)

    suspend fun setRoutineOccurrenceStatus(
        routineId: String,
        day: LocalDate,
        status: RoutineOccurrenceStatus,
    ): PlannedRoutineOccurrence = repository.setRoutineOccurrenceStatus(account.id, routineId, day, status)

    private fun reconcile(task: PlannerTask) {
        if (task.archived || task.status == dev.guilhermeluan.planner.tasks.TaskStatus.DONE) {
            reminders.cancel(task.id)
        } else {
            reminders.reconcile(task, account.timezone)
        }
    }
}
