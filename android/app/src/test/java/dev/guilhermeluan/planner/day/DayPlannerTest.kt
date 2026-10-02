package dev.guilhermeluan.planner.day

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.notifications.ReminderAdapter
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.testsupport.seed
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.tasks.IdGenerator
import dev.guilhermeluan.planner.tasks.PlannerTask
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.tasks.RoutineDraft
import dev.guilhermeluan.planner.tasks.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class DayPlannerTest {
    private lateinit var database: PlannerDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun `changing a Task persists it and reconciles its reminder`() = runTest {
        val account = Account("account-1", "Gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        database.seed(account, planner)
        val reminders = RecordingReminderAdapter()
        val ids = ArrayDeque(listOf("task-1"))
        val dayPlanner = DayPlanner(
            repository = RoomPlannerRepository(
                database,
                IdGenerator { ids.removeFirst() },
                Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
            ),
            reminders = reminders,
        )
        val day = LocalDate.of(2026, 7, 13)
        dayPlanner.bind(account, planner)

        val created = dayPlanner.createTask(TaskDraft("Estudar", day, LocalTime.of(18, 0)))

        assertEquals(listOf(created), dayPlanner.observeDay(day).first().tasks)
        assertEquals(listOf(created), reminders.scheduled)
    }

    @Test
    fun `marked days include days with Tarefas or Rotinas and skip archived and empty ones`() = runTest {
        val account = Account("account-1", "Gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        database.seed(account, planner)
        val ids = ArrayDeque(listOf("task-1", "task-2", "routine-1"))
        val dayPlanner = DayPlanner(
            RoomPlannerRepository(database, IdGenerator { ids.removeFirst() }, Clock.systemUTC()),
            RecordingReminderAdapter(),
        )
        dayPlanner.bind(account, planner)
        val monday = LocalDate.of(2026, 7, 13)
        dayPlanner.createTask(TaskDraft("Estudar", monday.plusDays(1), null))
        val archived = dayPlanner.createTask(TaskDraft("Velha", monday.plusDays(2), null))
        dayPlanner.archiveTask(archived.id)
        dayPlanner.createRoutine(
            RoutineDraft(
                "Alongar", setOf(DayOfWeek.FRIDAY), monday, null,
            ),
        )

        val marked = dayPlanner.observeMarkedDays(Week.of(monday)).first()

        assertEquals(setOf(monday.plusDays(1), monday.plusDays(4)), marked)
    }

    @Test
    fun `changing Fuso da Conta rebuilds reminders using the new timezone`() = runTest {
        val account = Account("account-1", "Gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        database.seed(account, planner)
        val reminders = RecordingReminderAdapter()
        val dayPlanner = DayPlanner(
            RoomPlannerRepository(database, IdGenerator { "unused" }, Clock.systemUTC()),
            reminders,
        )
        dayPlanner.bind(account, planner)

        dayPlanner.updateTimezone("Europe/Lisbon")

        assertEquals("Europe/Lisbon", reminders.rebuiltTimezone)
    }
}

private class RecordingReminderAdapter : ReminderAdapter {
    val scheduled = mutableListOf<PlannerTask>()
    var rebuiltTimezone: String? = null

    override fun reconcile(task: PlannerTask, timezone: String) {
        scheduled += task
    }

    override fun cancel(taskId: String) = Unit

    override fun rebuild(tasks: List<PlannerTask>, timezone: String) {
        rebuiltTimezone = timezone
    }
}
