package dev.guilhermeluan.planner.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.storage.AccountEntity
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.PlannerEntity
import dev.guilhermeluan.planner.storage.RoutineEntity
import dev.guilhermeluan.planner.storage.RoutineOccurrenceEntity
import dev.guilhermeluan.planner.storage.SessionMetadataEntity
import dev.guilhermeluan.planner.storage.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationToLocalTest {
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
    fun `fresh install has no account and migration is a no-op`() = runTest {
        val migration = MigrationToLocal(
            database = database,
            deviceTimezone = { "America/Sao_Paulo" },
        )

        migration.runIfNeeded()

        assertNull(database.sessionDao().activeAccountId())
        assertNull(database.sessionDao().metadata("migration_local_v1"))
    }

    @Test
    fun `existing installation migrates and preserves all data while cleaning server artifacts`() = runTest {
        setupExistingInstallation(
            timezone = "America/Sao_Paulo",
            username = "Guilherme",
            mustChangePassword = true,
        )

        val migration = MigrationToLocal(
            database = database,
            deviceTimezone = { "America/Fortaleza" },
        )

        migration.runIfNeeded()

        val account = database.sessionDao().account("account-1")
        assertNotNull(account)
        assertEquals("Guilherme", account!!.username)
        assertEquals("America/Sao_Paulo", account.timezone)
        assertEquals(false, account.mustChangePassword)

        val tasks = database.plannerDao().observeTasks("account-1", "2026-07-12").first()
        assertEquals(1, tasks.size)
        assertEquals("PENDING", tasks.single().status)

        val archivedTasks = database.plannerDao().observeArchivedTasks("account-1", "2026-07-12").first()
        assertEquals(1, archivedTasks.size)
        assertEquals("Arquivada", archivedTasks.single().title)

        val routines = database.routineDao().observeRoutines("account-1").first()
        assertEquals(1, routines.size)
        assertEquals("ACTIVE", routines.single().status)

        val occurrences = database.routineDao().observeOccurrences("account-1", "2026-07-12").first()
        assertEquals(1, occurrences.size)
        assertEquals("DONE", occurrences.single().status)

        assertEquals("1", database.sessionDao().metadata("migration_local_v1"))
    }

    @Test
    fun `migration failure preserves existing data and does not set marker`() = runTest {
        setupExistingInstallation(
            timezone = "America/Sao_Paulo",
            username = "Gui",
            mustChangePassword = true,
        )
        val migration = MigrationToLocal(
            database = database,
            deviceTimezone = { "America/Fortaleza" },
            clearServerConfig = { throw RuntimeException("Simulated failure") },
        )

        runCatching { migration.runIfNeeded() }

        val account = database.sessionDao().account("account-1")
        assertNotNull(account)
        assertEquals("Gui", account!!.username)
        assertEquals(false, account.mustChangePassword)
        assertEquals("America/Sao_Paulo", account.timezone)

        val tasks = database.plannerDao().observeTasks("account-1", "2026-07-12").first()
        assertEquals(1, tasks.size)
        assertEquals("PENDING", tasks.single().status)

        val routines = database.routineDao().observeRoutines("account-1").first()
        assertEquals(1, routines.size)

        assertNull(database.sessionDao().metadata("migration_local_v1"))
    }

    @Test
    fun `empty username clears session to trigger onboarding`() = runTest {
        setupExistingInstallation(
            timezone = "America/Sao_Paulo",
            username = "  ",
            mustChangePassword = true,
        )
        val migration = MigrationToLocal(
            database = database,
            deviceTimezone = { "America/Fortaleza" },
        )

        migration.runIfNeeded()

        assertNull(database.sessionDao().activeAccountId())
        assertNull(database.sessionDao().metadata("migration_local_v1"))
    }

    @Test
    fun `migration fixes invalid timezone to device timezone`() = runTest {
        setupExistingInstallation(
            timezone = "Bogus/Invalid",
            username = "Gui",
            mustChangePassword = false,
        )
        val migration = MigrationToLocal(
            database = database,
            deviceTimezone = { "America/Fortaleza" },
        )

        migration.runIfNeeded()

        val account = database.sessionDao().account("account-1")
        assertEquals("America/Fortaleza", account!!.timezone)
        assertEquals("1", database.sessionDao().metadata("migration_local_v1"))
    }

    @Test
    fun `migration is idempotent and does not duplicate data when run twice`() = runTest {
        setupExistingInstallation(
            timezone = "America/Sao_Paulo",
            username = "Gui",
            mustChangePassword = true,
        )
        val migration = MigrationToLocal(
            database = database,
            deviceTimezone = { "America/Fortaleza" },
        )

        migration.runIfNeeded()
        migration.runIfNeeded()

        val account = database.sessionDao().account("account-1")
        assertEquals("Gui", account!!.username)

        val tasks = database.plannerDao().observeTasks("account-1", "2026-07-12").first()
        assertEquals(1, tasks.size)

        assertEquals("1", database.sessionDao().metadata("migration_local_v1"))
    }

    private suspend fun setupExistingInstallation(
        timezone: String,
        username: String,
        mustChangePassword: Boolean,
    ) {
        val sessionDao = database.sessionDao()
        val plannerDao = database.plannerDao()
        val routineDao = database.routineDao()

        sessionDao.createLocalPlanner(
            account = AccountEntity("account-1", username, timezone, mustChangePassword),
            planner = PlannerEntity("planner-1", "account-1"),
        )

        plannerDao.writeLocalTask(
            TaskEntity("task-1", "account-1", "planner-1", "Pendente", "2026-07-12", null, "PENDING", false, 1, "2026-07-12T10:00:00Z"),
        )
        plannerDao.writeLocalTask(
            TaskEntity("task-2", "account-1", "planner-1", "Arquivada", "2026-07-12", null, "DONE", true, 2, "2026-07-12T11:00:00Z"),
        )

        routineDao.writeLocalRoutine(
            RoutineEntity("routine-1", "account-1", "planner-1", "Caminhar", "3", "2026-07-01", "07:30", "ACTIVE", 1, "2026-07-01T00:00:00Z"),
        )
        routineDao.writeLocalOccurrence(
            RoutineOccurrenceEntity("routine-1:2026-07-12", "account-1", "routine-1", "Caminhar", "2026-07-12", "07:30", "DONE", 1, "2026-07-12T07:30:00Z"),
        )

    }
}
