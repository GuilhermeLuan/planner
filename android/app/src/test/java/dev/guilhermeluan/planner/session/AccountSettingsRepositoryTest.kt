package dev.guilhermeluan.planner.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.storage.PlannerDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class AccountSettingsRepositoryTest {
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
    fun `changing timezone persists locally without outbox`() = runTest {
        val account = Account("account-1", "ana", "America/Sao_Paulo", false)
        RoomSessionStateStore(database).saveActive(account, Planner("planner-1", account.id))
        val repository = AccountSettingsRepository(database)

        repository.updateTimezone(account.id, "Europe/Lisbon")

        assertEquals("Europe/Lisbon", RoomSessionStateStore(database).readActive()?.first?.timezone)
        val outbox = database.plannerDao().pendingOperations(account.id)
        assertEquals(0, outbox.size)
    }

    @Test
    fun `changing name persists locally and rejects empty`() = runTest {
        val account = Account("account-1", "ana", "America/Sao_Paulo", false)
        RoomSessionStateStore(database).saveActive(account, Planner("planner-1", account.id))
        val repository = AccountSettingsRepository(database)

        repository.updateName(account.id, "Mariana")

        val updated = database.sessionDao().account("account-1")!!
        assertEquals("Mariana", updated.username)
        assertEquals("America/Sao_Paulo", updated.timezone)
        assertFalse(updated.mustChangePassword)

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.updateName(account.id, "  ") }
        }
    }

    @Test
    fun `invalid timezone is rejected before changing account`() = runTest {
        val account = Account("account-1", "ana", "America/Sao_Paulo", false)
        RoomSessionStateStore(database).saveActive(account, Planner("planner-1", account.id))
        val repository = AccountSettingsRepository(database)

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.updateTimezone(account.id, "Mars/Olympus_Mons") }
        }
        assertEquals("America/Sao_Paulo", RoomSessionStateStore(database).readActive()?.first?.timezone)
    }
}
