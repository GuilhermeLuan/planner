package dev.guilhermeluan.planner.you

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.backup.BackupExporter
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.LocalPlanner
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.tasks.IdGenerator
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.tasks.TaskDraft
import dev.guilhermeluan.planner.testsupport.seed
import dev.guilhermeluan.planner.water.WaterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class YouViewModelTest {
    private lateinit var database: PlannerDatabase
    private lateinit var planner: RoomPlannerRepository
    private lateinit var backup: BackupExporter
    private val account = Account("account-1", "Ana", "America/Sao_Paulo", false)
    private val plannerRef = Planner("planner-1", account.id)
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
        override fun instant(): Instant = Instant.parse("2026-10-01T01:30:00Z") // 30/09 22:30 em São Paulo
    }
    private val viewModels = mutableListOf<YouViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        runBlocking { database.seed(account, plannerRef) }
        var next = 0
        planner = RoomPlannerRepository(database, IdGenerator { "id-${next++}" }, clock)
        backup = BackupExporter(database, clock)
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext[Job]!!.children.forEach { job -> job.cancelAndJoin() } } }
        database.close()
        Dispatchers.resetMain()
    }

    private fun boundViewModel(localPlanner: LocalPlanner = LocalPlanner(account, plannerRef)) =
        YouViewModel(ConsistencyRepository(database, planner, WaterRepository(database, clock), clock), planner, backup, clock)
            .also {
                viewModels += it
                it.bind(localPlanner)
            }

    private suspend fun YouViewModel.loaded(): YouUiState =
        withTimeout(5_000) { uiState.first { it.memberSince != null && it.consistency != null } }

    @Test
    fun stateCarriesTheProfileOfTheAccountAndTodayInTheAccountZone() = runBlocking {
        val state = boundViewModel().loaded()

        assertEquals("Ana", state.name)
        assertEquals("America/Sao_Paulo", state.timezone)
        assertEquals(LocalDate.of(2026, 9, 30), state.today)
        assertEquals(YearMonth.of(2026, 9), state.memberSince)
    }

    @Test
    fun stateCarriesTheConsistencyAndWhatIsArchived() = runBlocking {
        val task = planner.createTask(account.id, plannerRef.id, TaskDraft("Pagar conta", LocalDate.of(2026, 9, 3), null))
        planner.archiveTask(account.id, task.id)

        val state = withTimeout(5_000) { boundViewModel().uiState.first { it.archived.tasks.isNotEmpty() } }

        assertEquals(Consistency(0, null, 0), state.consistency)
        assertEquals(listOf("Pagar conta"), state.archived.tasks.map { it.title })
    }

    @Test
    fun lastBackupAppearsAsADayInTheAccountZoneOnceSaved() = runBlocking {
        val viewModel = boundViewModel()
        assertNull(viewModel.loaded().lastBackup)

        viewModel.backupSaved()

        val state = withTimeout(5_000) { viewModel.uiState.first { it.lastBackup != null } }
        assertEquals(LocalDate.of(2026, 9, 30), state.lastBackup)
    }

    @Test
    fun changingTheNameOrTheZoneRebindsTheState() = runBlocking {
        val viewModel = boundViewModel()
        viewModel.loaded()

        viewModel.bind(LocalPlanner(account.copy(username = "Gui", timezone = "America/Manaus"), plannerRef))

        val state = withTimeout(5_000) { viewModel.uiState.first { it.name == "Gui" && it.consistency != null } }
        assertEquals("America/Manaus", state.timezone)
        assertEquals(LocalDate.of(2026, 9, 30), state.today)
    }
}
