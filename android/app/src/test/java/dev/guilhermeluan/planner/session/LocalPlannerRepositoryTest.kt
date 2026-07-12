package dev.guilhermeluan.planner.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.storage.PlannerDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalPlannerRepositoryTest {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: LocalPlannerRepository
    private val ids = ArrayDeque(listOf("account-local", "planner-local"))

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LocalPlannerRepository(database) { ids.removeFirst() }
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun `first onboarding creates and restores the same local Planner`() = runTest {
        assertNull(repository.restorePlanner())

        val created = repository.createPlanner("  Guilherme  ", "America/Sao_Paulo")

        assertEquals("account-local", created.account.id)
        assertEquals("Guilherme", created.account.username)
        assertEquals("America/Sao_Paulo", created.account.timezone)
        assertEquals("planner-local", created.planner.id)
        assertEquals(created, repository.restorePlanner())
    }

    @Test
    fun `onboarding rejects a name made only of spaces`() = runTest {
        val error = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.createPlanner("   ", "America/Sao_Paulo") }
        }

        assertEquals("Informe seu nome", error.message)
        assertNull(repository.restorePlanner())
    }
}
