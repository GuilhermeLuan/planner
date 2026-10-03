package dev.guilhermeluan.planner.water

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.LocalPlanner
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.testsupport.seed
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WaterViewModelTest {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: WaterRepository
    private val account = Account("account-1", "ana", "America/Sao_Paulo", false)
    private val planner = Planner("planner-1", account.id)

    // 22:30 de 1/10 em São Paulo, já 2/10 em UTC.
    private var now = Instant.parse("2026-10-02T01:30:00Z")
    private val clock = clockIn(ZoneOffset.UTC)

    private fun clockIn(zone: ZoneId): Clock = object : Clock() {
        override fun getZone() = zone
        override fun withZone(zone: ZoneId) = clockIn(zone)
        override fun instant(): Instant = now
    }
    private val september30 = LocalDate.of(2026, 9, 30)
    private val october1 = LocalDate.of(2026, 10, 1)
    private val october2 = LocalDate.of(2026, 10, 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        runBlocking { database.seed(account, planner) }
        repository = WaterRepository(database, clock)
    }

    private val viewModels = mutableListOf<WaterViewModel>()

    @After
    fun tearDown() {
        // A observação do Dia pode seguir depois das asserções; ela para antes de o banco fechar.
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext[Job]!!.children.forEach { job -> job.cancelAndJoin() } } }
        database.close()
        Dispatchers.resetMain()
    }

    private fun boundViewModel() = WaterViewModel(repository, clock).also {
        viewModels += it
        it.bind(LocalPlanner(account, planner))
    }

    @Test
    fun opensOnTodayInTheAccountTimezoneWithItsWeek() = runBlocking {
        repository.add(account.id, october1, 600)

        val state = withTimeout(5_000) { boundViewModel().uiState.first { it.week.isNotEmpty() } }

        assertEquals(WaterDay(october1, 600, 2000), state.day)
        assertEquals(LocalDate.of(2026, 9, 28), state.week.first().day)
        assertEquals(7, state.week.size)
    }

    @Test
    fun addingAndAdjustingAlwaysGoToTodayInTheAccountTimezone() = runBlocking {
        val viewModel = boundViewModel()

        viewModel.add(500)
        withTimeout(5_000) { repository.observeDay(account.id, october1).first { it.consumedMl == 500 } }
        viewModel.adjustTotal(300)

        val state = withTimeout(5_000) { viewModel.uiState.first { it.day.consumedMl == 300 } }
        assertEquals(october1, state.day.day)
        assertEquals("2/10 já é hoje em UTC, mas não no Fuso da Conta", 0, repository.observeDay(account.id, october2).first().consumedMl)
    }

    @Test
    fun changingTheGoalAppliesFromTodayWithoutRewritingYesterday() = runBlocking {
        val viewModel = boundViewModel()

        viewModel.setGoal(2500)

        withTimeout(5_000) { repository.observeDay(account.id, october1).first { it.goalMl == 2500 } }
        assertEquals(2000, repository.observeDay(account.id, september30).first().goalMl)
    }

    @Test
    fun afterMidnightTheTabMovesToTheNewDay() = runBlocking {
        val viewModel = boundViewModel()
        withTimeout(5_000) { viewModel.uiState.first { it.week.isNotEmpty() } }

        now = Instant.parse("2026-10-02T03:01:00Z") // 00:01 de 2/10 em São Paulo
        viewModel.refreshToday()
        viewModel.add(200)

        val state = withTimeout(5_000) { viewModel.uiState.first { it.day.day == october2 && it.day.consumedMl == 200 } }
        assertEquals(0, repository.observeDay(account.id, october1).first().consumedMl)
        assertEquals(october2, state.day.day)
    }

    @Test
    fun changingTheAccountTimezoneMovesTodayToTheNewTimezone() = runBlocking {
        val viewModel = boundViewModel()

        viewModel.updateTimezone("Asia/Tokyo")
        viewModel.add(200)

        withTimeout(5_000) { viewModel.uiState.first { it.day.day == october2 && it.day.consumedMl == 200 } }
        assertEquals(0, repository.observeDay(account.id, october1).first().consumedMl)
    }
}
