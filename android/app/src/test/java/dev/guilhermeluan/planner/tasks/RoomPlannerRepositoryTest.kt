package dev.guilhermeluan.planner.tasks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.testsupport.seed
import dev.guilhermeluan.planner.storage.PlannerDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RoomPlannerRepositoryTest {
    private lateinit var database: PlannerDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `creating a Task offline updates the Day immediately`() = runTest {
        val account = Account("account-1", "gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        database.seed(account, planner)
        val repository = RoomPlannerRepository(
            database = database,
            idGenerator = SequenceIdGenerator("task-1", "operation-1"),
            clock = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC),
        )
        val day = LocalDate.of(2026, 7, 11)

        val task = repository.createTask(
            accountId = account.id,
            plannerId = planner.id,
            draft = TaskDraft(title = "Enviar documentos", day = day, time = null),
        )

        assertEquals(
            DayPlan(day = day, tasks = listOf(task), routines = emptyList()),
            repository.observeDay(account.id, day).first(),
        )
    }

    @Test
    fun `rescheduling moves the same Task to another Day without duplication`() = runTest {
        val account = Account("account-1", "gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        database.seed(account, planner)
        val repository = RoomPlannerRepository(
            database,
            SequenceIdGenerator("task-1", "create-operation", "reschedule-operation"),
            Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC),
        )
        val originalDay = LocalDate.of(2026, 7, 11)
        val nextDay = originalDay.plusDays(1)
        val task = repository.createTask(
            account.id,
            planner.id,
            TaskDraft("Enviar documentos", originalDay, null),
        )

        val moved = repository.rescheduleTask(account.id, task.id, nextDay)

        assertEquals(task.id, moved.id)
        assertEquals(emptyList<PlannerTask>(), repository.observeDay(account.id, originalDay).first().tasks)
        assertEquals(listOf(moved), repository.observeDay(account.id, nextDay).first().tasks)
    }

    @Test
    fun `archiving and restoring a Task is reversible`() = runTest {
        val account = Account("account-1", "gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        database.seed(account, planner)
        val repository = RoomPlannerRepository(
            database,
            SequenceIdGenerator(
                "task-1",
                "create-operation",
                "archive-operation",
                "restore-operation",
            ),
            Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC),
        )
        val day = LocalDate.of(2026, 7, 11)
        val task = repository.createTask(
            account.id,
            planner.id,
            TaskDraft("Enviar documentos", day, null),
        )

        val archived = repository.archiveTask(account.id, task.id)
        assertEquals(true, archived.archived)
        assertEquals(emptyList<PlannerTask>(), repository.observeDay(account.id, day).first().tasks)

        val restored = repository.restoreTask(account.id, task.id)
        assertEquals(false, restored.archived)
        assertEquals(listOf(restored), repository.observeDay(account.id, day).first().tasks)
    }

    @Test
    fun `editing changes title and optional time on the same Task`() = runTest {
        val account = Account("account-1", "gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        database.seed(account, planner)
        val repository = RoomPlannerRepository(
            database,
            SequenceIdGenerator("task-1", "create-operation", "edit-operation"),
            Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC),
        )
        val day = LocalDate.of(2026, 7, 11)
        val task = repository.createTask(
            account.id,
            planner.id,
            TaskDraft("Documentos", day, null),
        )

        val edited = repository.editTask(
            accountId = account.id,
            taskId = task.id,
            title = "Enviar documentos",
            time = LocalTime.of(8, 30),
        )

        assertEquals(task.id, edited.id)
        assertEquals("Enviar documentos", edited.title)
        assertEquals(LocalTime.of(8, 30), edited.time)
        assertEquals(listOf(edited), repository.observeDay(account.id, day).first().tasks)
    }
}

private class SequenceIdGenerator(vararg ids: String) : IdGenerator {
    private val values = ArrayDeque(ids.toList())
    override fun nextId(): String = values.removeFirst()
}
