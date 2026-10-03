package dev.guilhermeluan.planner.tasks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.testsupport.seed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class ArchivedItemsTest {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: RoomPlannerRepository
    private val account = Account("account-1", "ana", "America/Sao_Paulo", false)
    private val planner = Planner("planner-1", account.id)
    private val day = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.seed(account, planner)
        var next = 0
        repository = RoomPlannerRepository(
            database, IdGenerator { "id-${next++}" }, Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun archived() = repository.observeArchivedItems(account.id).first()

    @Test
    fun nothingIsArchivedAtFirst() = runTest {
        repository.createTask(account.id, planner.id, TaskDraft("Pagar conta", day, null))

        assertTrue(archived().isEmpty)
    }

    @Test
    fun archivedTasksRoutinesAndMedicinesAreGatheredInOnePlace() = runTest {
        val task = repository.createTask(account.id, planner.id, TaskDraft("Pagar conta", day, null))
        val laterTask = repository.createTask(account.id, planner.id, TaskDraft("Ligar para o banco", day.plusDays(9), null))
        val routine = repository.createRoutine(account.id, planner.id, RoutineDraft("Alongar", setOf(DayOfWeek.MONDAY), day, null))
        val medicine = repository.createMedicine(
            account.id, planner.id,
            MedicineDraft("Losartana", 1, DoseUnit.TABLET, setOf(LocalTime.of(8, 0)), MedicineRepeat.Daily, day),
        )
        repository.createTask(account.id, planner.id, TaskDraft("Segue ativa", day, null))
        repository.archiveTask(account.id, task.id)
        repository.archiveTask(account.id, laterTask.id)
        repository.archiveRoutine(account.id, routine.id)
        repository.archiveMedicine(account.id, medicine.id, day)

        val items = archived()

        assertEquals(listOf("Pagar conta", "Ligar para o banco"), items.tasks.map { it.title })
        assertEquals(listOf("Alongar"), items.routines.map { it.title })
        assertEquals(listOf("Losartana"), items.medicines.map { it.name })
    }

    @Test
    fun restoringAnItemTakesItOutOfTheArchive() = runTest {
        val task = repository.createTask(account.id, planner.id, TaskDraft("Pagar conta", day, null))
        val routine = repository.createRoutine(account.id, planner.id, RoutineDraft("Alongar", setOf(DayOfWeek.MONDAY), day, null))
        repository.archiveTask(account.id, task.id)
        repository.archiveRoutine(account.id, routine.id)

        repository.restoreTask(account.id, task.id)
        repository.restoreRoutine(account.id, routine.id)

        assertTrue(archived().isEmpty)
    }
}
