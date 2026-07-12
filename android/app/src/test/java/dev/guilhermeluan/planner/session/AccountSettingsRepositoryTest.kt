package dev.guilhermeluan.planner.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.storage.PlannerDatabase
import kotlinx.coroutines.test.runTest
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
    fun `changing timezone persists locally and enqueues account settings`() = runTest {
        val account = Account("account-1", "ana", "America/Sao_Paulo", false)
        RoomSessionStateStore(database).saveActive(account, Planner("planner-1", account.id))
        val repository = AccountSettingsRepository(
            database,
            Ids("timezone-operation"),
            Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC),
        )

        repository.updateTimezone(account.id, "Europe/Lisbon")

        assertEquals("Europe/Lisbon", RoomSessionStateStore(database).readActive()?.first?.timezone)
        val operation = database.plannerDao().pendingOperations(account.id).single()
        assertEquals("account_settings", operation.entityType)
        assertEquals("Europe/Lisbon", org.json.JSONObject(operation.payloadJson).getString("timezone"))
    }

    @Test
    fun `invalid timezone is rejected before changing account`() = runTest {
        val account = Account("account-1", "ana", "America/Sao_Paulo", false)
        RoomSessionStateStore(database).saveActive(account, Planner("planner-1", account.id))
        val repository = AccountSettingsRepository(database, Ids("unused"), Clock.systemUTC())

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.updateTimezone(account.id, "Mars/Olympus_Mons") }
        }
        assertEquals("America/Sao_Paulo", RoomSessionStateStore(database).readActive()?.first?.timezone)
    }
}

private class Ids(vararg ids: String) : dev.guilhermeluan.planner.tasks.IdGenerator {
    private val values = ArrayDeque(ids.toList())
    override fun nextId(): String = values.removeFirst()
}
