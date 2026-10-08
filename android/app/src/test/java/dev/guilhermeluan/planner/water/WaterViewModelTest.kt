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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

    // Cada reagendamento dos Lembretes de água chega aqui, na ordem em que aconteceu.
    private val waterChanges = Channel<Pair<String, String>>(Channel.UNLIMITED)

    @After
    fun tearDown() {
        // A observação do Dia pode seguir depois das asserções; ela para antes de o banco fechar.
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext[Job]!!.children.forEach { job -> job.cancelAndJoin() } } }
        database.close()
        Dispatchers.resetMain()
    }

    private fun boundViewModel() = WaterViewModel(repository, clock, onWaterChanged = { accountId, timezone ->
        waterChanges.send(accountId to timezone)
    }).also {
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

    @Test
    fun changingTheAccountTimezoneRebuildsTheWaterRemindersInTheNewTimezone() = runBlocking {
        val viewModel = boundViewModel()

        viewModel.updateTimezone("Asia/Tokyo")

        assertEquals(account.id to "Asia/Tokyo", withTimeout(5_000) { waterChanges.receive() })
    }

    @Test
    fun viewedDayFollowsTheSelectedDayWhileTodayStaysTheTabDay() = runBlocking {
        repository.add(account.id, october1, 600)
        repository.add(account.id, september30, 1500)
        val viewModel = boundViewModel()

        viewModel.viewDay(september30)

        val state = withTimeout(5_000) { viewModel.uiState.first { it.viewedDay.day == september30 && it.viewedDay.consumedMl == 1500 } }
        assertEquals(WaterDay(october1, 600, 2000), withTimeout(5_000) { viewModel.uiState.first { it.day.consumedMl == 600 } }.day)
        assertEquals(1500, state.viewedDay.consumedMl)
    }

    @Test
    fun viewedDayStartsOnToday() = runBlocking {
        repository.add(account.id, october1, 600)

        val state = withTimeout(5_000) { boundViewModel().uiState.first { it.viewedDay.consumedMl == 600 } }

        assertEquals(october1, state.viewedDay.day)
    }

    @Test
    fun addingAdjustingAndSettingTheGoalRebuildTheWaterRemindersInTheAccountTimezone() = runBlocking {
        val viewModel = boundViewModel()

        viewModel.add(500)
        viewModel.adjustTotal(300)
        viewModel.setGoal(2500)

        repeat(3) { assertEquals(account.id to "America/Sao_Paulo", withTimeout(5_000) { waterChanges.receive() }) }
    }

    @Test
    fun savingTheReminderSavesItAndRebuildsTheWaterRemindersInTheAccountTimezone() = runBlocking {
        val viewModel = boundViewModel()
        val settings = WaterReminderSettings(enabled = true, intervalHours = 1, windowStart = LocalTime.of(7, 30), windowEnd = LocalTime.of(22, 0))

        viewModel.saveReminderSettings(settings)

        assertEquals(account.id to "America/Sao_Paulo", withTimeout(5_000) { waterChanges.receive() })
        assertEquals(settings, repository.observeReminderSettings(account.id).first())
    }

    @Test
    fun invalidReminderIsRefusedWithoutCrashingOrRebuildingTheWaterReminders() = runBlocking {
        val viewModel = boundViewModel()
        // Sem a checagem de validade, o erro do repositório escaparia da corrotina até o handler de exceções não tratadas, que derruba o app.
        val escaped = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val handler = thread.uncaughtExceptionHandler
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> escaped += error }
        try {
            viewModel.saveReminderSettings(WaterReminderSettings(enabled = true, intervalHours = 4))
            viewModel.saveReminderSettings(WaterReminderSettings(enabled = true, windowStart = LocalTime.of(20, 0), windowEnd = LocalTime.of(8, 0)))
        } finally {
            thread.uncaughtExceptionHandler = handler
        }

        assertEquals(emptyList<Throwable>(), escaped)
        assertNull(waterChanges.tryReceive().getOrNull())
        assertEquals(WaterReminderSettings(), repository.observeReminderSettings(account.id).first())
    }

    @Test
    fun reminderIsOffUntilTheAccountSavesOneAndSurvivesWaterChanges() = runBlocking {
        val viewModel = boundViewModel()
        assertEquals(WaterReminderSettings(), withTimeout(5_000) { viewModel.uiState.first { it.week.isNotEmpty() } }.reminder)

        val saved = WaterReminderSettings(enabled = true, intervalHours = 3, windowStart = LocalTime.of(9, 0), windowEnd = LocalTime.of(21, 30))
        repository.saveReminderSettings(account.id, saved)
        withTimeout(5_000) { viewModel.uiState.first { it.reminder == saved } }

        viewModel.add(200)
        withTimeout(5_000) { viewModel.uiState.first { it.day.consumedMl == 200 } }
        assertEquals(saved, viewModel.uiState.value.reminder)
    }
}
