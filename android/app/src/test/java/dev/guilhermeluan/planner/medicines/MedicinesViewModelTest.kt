package dev.guilhermeluan.planner.medicines

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.LocalPlanner
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.testsupport.seed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MedicinesViewModelTest {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: RoomPlannerRepository
    private val account = Account("account-1", "ana", "America/Sao_Paulo", false)
    private val planner = Planner("planner-1", account.id)

    // 22:30 de 1/10 em São Paulo, já 10:30 de 2/10 em Tóquio.
    private val clock = Clock.fixed(Instant.parse("2026-10-02T01:30:00Z"), ZoneOffset.UTC)
    private val october1 = LocalDate.of(2026, 10, 1)
    private val october2 = LocalDate.of(2026, 10, 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        var next = 0
        repository = RoomPlannerRepository(database, { "id-${next++}" }, clock)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun afterTheAccountTimezoneChangesArchivingUsesTodayInTheNewTimezone() = runBlocking {
        database.seed(account, planner)
        val medicine = repository.createMedicine(
            account.id,
            planner.id,
            MedicineDraft("Vitamina D", 1, DoseUnit.CAPSULE, setOf(LocalTime.of(13, 0)), MedicineRepeat.Daily, october1),
        )
        val viewModel = MedicinesViewModel(repository, clock)
        viewModel.bind(LocalPlanner(account, planner))

        viewModel.updateTimezone("Asia/Tokyo")
        viewModel.archiveMedicine(medicine.id)

        withTimeout(5_000) { repository.observeArchivedMedicines(account.id).first { it.isNotEmpty() } }
        assertEquals("1/10 ainda é antes de arquivar", 1, repository.observeDoses(account.id, october1).first().size)
        assertEquals(0, repository.observeDoses(account.id, october2).first().size)
    }

    @Test
    fun stateCarriesTheLastRegisteredDayOfEachMedicineForTheEditForm() = runBlocking {
        database.seed(account, planner)
        val medicine = repository.createMedicine(
            account.id,
            planner.id,
            MedicineDraft("Vitamina D", 1, DoseUnit.CAPSULE, setOf(LocalTime.of(13, 0)), MedicineRepeat.Daily, october1),
        )
        val viewModel = MedicinesViewModel(repository, clock)
        viewModel.bind(LocalPlanner(account, planner))

        repository.setDoseStatus(account.id, medicine.id, october1, LocalTime.of(13, 0), DoseStatus.TAKEN)

        val state = withTimeout(5_000) { viewModel.uiState.first { it.lastRegisteredDays.isNotEmpty() } }
        assertEquals(mapOf(medicine.id to october1), state.lastRegisteredDays)
    }
}
