package dev.guilhermeluan.planner.tasks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.session.RoomSessionStateStore
import dev.guilhermeluan.planner.storage.PlannerDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class RoutineRepositoryTest {
    private lateinit var database: PlannerDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `routine projects an occurrence on selected weekdays and writes locally`() = runTest {
        val account = Account("account-1", "ana", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        RoomSessionStateStore(database).saveActive(account, planner)
        val repository = RoomPlannerRepository(
            database,
            Ids("routine-1", "routine-operation"),
            FIXED_CLOCK,
        )
        val day = LocalDate.of(2026, 7, 8)

        repository.createRoutine(
            account.id,
            planner.id,
            RoutineDraft("Caminhar", setOf(DayOfWeek.WEDNESDAY), day.minusDays(2), LocalTime.of(7, 30)),
        )

        val plan = repository.observeDay(account.id, day).first()
        assertEquals(1, plan.routines.size)
        assertEquals("Caminhar", plan.routines.single().title)
        assertEquals(LocalTime.of(7, 30), plan.routines.single().time)
        assertTrue(database.plannerDao().pendingOperations(account.id).isEmpty())
    }

    @Test
    fun `occurrence status is independent and archiving preserves the recorded occurrence`() = runTest {
        val account = Account("account-1", "ana", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        RoomSessionStateStore(database).saveActive(account, planner)
        val repository = RoomPlannerRepository(
            database,
            Ids("routine-1", "create-routine", "occurrence-operation", "archive-operation"),
            FIXED_CLOCK,
        )
        val day = LocalDate.of(2026, 7, 8)
        val routine = repository.createRoutine(
            account.id,
            planner.id,
            RoutineDraft("Caminhar", setOf(DayOfWeek.WEDNESDAY), day, null),
        )

        repository.setRoutineOccurrenceStatus(account.id, routine.id, day, RoutineOccurrenceStatus.DONE)
        repository.archiveRoutine(account.id, routine.id)

        advanceUntilIdle()
        val plan = repository.observeDay(account.id, day).first()
        assertEquals(RoutineOccurrenceStatus.DONE, plan.routines.single().status)
        assertTrue(database.plannerDao().pendingOperations(account.id).isEmpty())
    }

    private companion object {
        val FIXED_CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-07-11T12:00:00Z"),
            ZoneOffset.UTC,
        )
    }
}

private class Ids(vararg ids: String) : IdGenerator {
    private val values = ArrayDeque(ids.toList())
    override fun nextId(): String = values.removeFirst()
}
