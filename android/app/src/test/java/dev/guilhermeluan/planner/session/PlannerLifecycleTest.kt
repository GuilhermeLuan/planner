package dev.guilhermeluan.planner.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.backup.BackupExporter
import dev.guilhermeluan.planner.storage.PlannerDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
        val ids = ArrayDeque(listOf("account-1", "planner-1"))
        val lifecycle = PlannerLifecycle(
            repository = LocalPlannerRepository(database) { ids.removeFirst() },
            settings = AccountSettingsRepository(database),
            migration = MigrationToLocal(database, { "America/Sao_Paulo" }),
            backup = BackupExporter(
                database,
                Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
            ),
        )

        val created = lifecycle.create("Gui", "America/Sao_Paulo")

        assertEquals(created, lifecycle.restore())
    }

    @Test
    fun `backup always exports the active Conta without caller supplied identity`() = runTest {
        val ids = ArrayDeque(listOf("account-1", "planner-1"))
        val lifecycle = PlannerLifecycle(
            LocalPlannerRepository(database) { ids.removeFirst() },
            AccountSettingsRepository(database),
            MigrationToLocal(database, { "America/Sao_Paulo" }),
            BackupExporter(database, Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC)),
        )
        lifecycle.create("Gui", "America/Sao_Paulo")

        val backup = lifecycle.exportBackup()

        assertEquals("account-1", backup.getJSONObject("conta").getString("id"))
        assertEquals("planner-1", backup.getJSONObject("planner").getString("id"))
    }
}
