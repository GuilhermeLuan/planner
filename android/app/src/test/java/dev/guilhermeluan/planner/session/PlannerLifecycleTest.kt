package dev.guilhermeluan.planner.session

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.backup.Backup
import dev.guilhermeluan.planner.backup.BackupExporter
import dev.guilhermeluan.planner.backup.BackupImporter
import dev.guilhermeluan.planner.backup.BackupReader
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.TaskEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class PlannerLifecycleTest {
    private lateinit var database: PlannerDatabase
    private val clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC)
    private val reschedules = mutableListOf<List<String>>()
    private val importedPlanner = LocalPlanner(
        account = Account(id = "acc-1", username = "Ana", timezone = "America/Sao_Paulo", mustChangePassword = false),
        planner = Planner(id = "plan-1", accountId = "acc-1"),
    )

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
    fun `creating a Planner makes the same local Planner restorable`() = runTest {
        val lifecycle = lifecycle()

        val created = lifecycle.create("Gui", "America/Sao_Paulo")

        assertEquals(created, lifecycle.restore())
    }

    @Test
    fun `backup always exports the active Conta without caller supplied identity`() = runTest {
        val lifecycle = lifecycle()
        lifecycle.create("Gui", "America/Sao_Paulo")

        val backup = lifecycle.exportBackup()

        assertEquals("account-1", backup.getJSONObject("conta").getString("id"))
        assertEquals("planner-1", backup.getJSONObject("planner").getString("id"))
    }

    @Test
    fun `importing a backup makes the imported Planner the restorable one`() = runTest {
        val lifecycle = lifecycle()
        lifecycle.create("Gui", "America/Sao_Paulo")
        seedOldTask()

        val imported = lifecycle.importBackup(fixtureBackup())

        assertEquals(importedPlanner, imported)
        assertEquals(importedPlanner, lifecycle.restore())
    }

    @Test
    fun `importing a backup reschedules once with the ids of the replaced Planner's Tasks`() = runTest {
        val lifecycle = lifecycle()
        lifecycle.create("Gui", "America/Sao_Paulo")
        seedOldTask()

        lifecycle.importBackup(fixtureBackup())

        assertEquals(listOf(listOf("task-old")), reschedules)
    }

    @Test
    fun `backup after importing exports the imported Conta`() = runTest {
        val lifecycle = lifecycle()
        lifecycle.create("Gui", "America/Sao_Paulo")
        lifecycle.importBackup(fixtureBackup())

        val backup = lifecycle.exportBackup()

        assertEquals("acc-1", backup.getJSONObject("conta").getString("id"))
    }

    @Test
    fun `failed import keeps the replaced Planner active and reschedules nothing`() = runTest {
        val lifecycle = lifecycle()
        lifecycle.create("Gui", "America/Sao_Paulo")
        val backup = fixtureBackup()
        val dose = backup.doses.first()
        val broken = backup.copy(doses = listOf(dose.copy(medicineId = "nao-existe")))

        val error = runCatching { lifecycle.importBackup(broken) }.exceptionOrNull()

        assertTrue(error is SQLiteConstraintException)
        assertEquals(emptyList<List<String>>(), reschedules)
        assertEquals("account-1", lifecycle.exportBackup().getJSONObject("conta").getString("id"))
    }

    @Test
    fun `failed reschedule does not fail the import`() = runTest {
        val lifecycle = lifecycle(reschedule = { throw RuntimeException("Alarme indisponível") })
        lifecycle.create("Gui", "America/Sao_Paulo")
        seedOldTask()

        val imported = lifecycle.importBackup(fixtureBackup())

        assertEquals(importedPlanner, imported)
        assertEquals(importedPlanner, lifecycle.restore())
    }

    @Test
    fun `cancellation while rescheduling is not swallowed`() = runTest {
        val lifecycle = lifecycle(reschedule = { throw CancellationException("Cancelado") })
        lifecycle.create("Gui", "America/Sao_Paulo")

        val error = runCatching { lifecycle.importBackup(fixtureBackup()) }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }

    /** Lifecycle com ids previsíveis; por padrão, as listas de ids recebidas pelo reagendamento ficam em [reschedules]. */
    private fun lifecycle(
        reschedule: suspend (removedTaskIds: List<String>) -> Unit = { removedTaskIds -> reschedules.add(removedTaskIds) },
    ): PlannerLifecycle {
        val ids = ArrayDeque(listOf("account-1", "planner-1"))
        return PlannerLifecycle(
            repository = LocalPlannerRepository(database) { ids.removeFirst() },
            settings = AccountSettingsRepository(database),
            migration = MigrationToLocal(database, { "America/Sao_Paulo" }),
            backup = BackupExporter(database, clock),
            importer = BackupImporter(database, clock),
            reschedule = reschedule,
        )
    }

    /** Tarefa gravada no Planner criado pelo teste, para a importação ter o que apagar. */
    private suspend fun seedOldTask() {
        database.plannerDao().writeLocalTask(
            TaskEntity(
                id = "task-old", accountId = "account-1", plannerId = "planner-1",
                title = "Tarefa antiga", day = "2026-07-12", time = "09:00",
                status = "PENDING", archived = false, version = 0, updatedAt = "2026-07-01T10:00:00Z",
            ),
        )
    }

    private fun fixtureBackup(): Backup =
        BackupReader.read(javaClass.getResource("/backup/backup-v2.json")!!.readText())
}
