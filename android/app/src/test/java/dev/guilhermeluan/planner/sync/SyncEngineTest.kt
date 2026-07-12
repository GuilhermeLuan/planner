package dev.guilhermeluan.planner.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.session.RoomSessionStateStore
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.tasks.IdGenerator
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.tasks.TaskDraft
import dev.guilhermeluan.planner.tasks.TaskStatus
import kotlinx.coroutines.flow.first
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
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    private lateinit var database: PlannerDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `confirmed offline Task is sent once and leaves the outbox`() = runTest {
        val account = Account("account-1", "gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        RoomSessionStateStore(database).saveActive(account, planner)
        val repository = RoomPlannerRepository(
            database,
            QueueIdGenerator("task-1", "operation-1"),
            FIXED_CLOCK,
        )
        repository.createTask(
            account.id,
            planner.id,
            TaskDraft("Enviar documentos", LocalDate.of(2026, 7, 11), null),
        )
        val api = RecordingSyncApi()
        val engine = SyncEngine(database, api, SyncSession(account.id, "token-1"), FIXED_CLOCK)

        assertEquals(SyncStatus.Synced, engine.syncOnce())
        assertEquals(SyncStatus.Synced, engine.syncOnce())

        assertEquals(1, api.receivedOperations.size)
        assertEquals("operation-1", api.receivedOperations.single().operationId)
        assertEquals("task", api.receivedOperations.single().entityType)
        assertEquals("task-1", api.receivedOperations.single().entityId)
    }

    @Test
    fun `second client observes Task through incremental pull`() = runTest {
        val account = Account("account-1", "gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        RoomSessionStateStore(database).saveActive(account, planner)
        val secondDatabase = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            RoomSessionStateStore(secondDatabase).saveActive(account, planner)
            val firstRepository = RoomPlannerRepository(
                database,
                QueueIdGenerator("task-1", "operation-1"),
                FIXED_CLOCK,
            )
            val secondRepository = RoomPlannerRepository(
                secondDatabase,
                QueueIdGenerator("unused"),
                FIXED_CLOCK,
            )
            val day = LocalDate.of(2026, 7, 11)
            firstRepository.createTask(
                account.id,
                planner.id,
                TaskDraft("Enviar documentos", day, null),
            )
            val api = CanonicalSyncApi()

            SyncEngine(database, api, SyncSession(account.id, "device-1"), FIXED_CLOCK).syncOnce()
            val secondEngine = SyncEngine(
                secondDatabase,
                api,
                SyncSession(account.id, "device-2"),
                FIXED_CLOCK,
            )
            secondEngine.syncOnce()
            secondEngine.syncOnce()

            val received = secondRepository.observeDay(account.id, day).first().tasks
            assertEquals(1, received.size)
            assertEquals("Enviar documentos", received.single().title)
            assertEquals(1, received.single().version)
            assertTrue(api.pullCursors.containsAll(listOf(0L, 1L)))
        } finally {
            secondDatabase.close()
        }
    }

    @Test
    fun `completed Task is observed as completed by a second client`() = runTest {
        val account = Account("account-1", "gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        RoomSessionStateStore(database).saveActive(account, planner)
        val secondDatabase = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            RoomSessionStateStore(secondDatabase).saveActive(account, planner)
            val firstRepository = RoomPlannerRepository(
                database,
                QueueIdGenerator("task-1", "create-operation", "complete-operation"),
                FIXED_CLOCK,
            )
            val secondRepository = RoomPlannerRepository(
                secondDatabase,
                QueueIdGenerator("unused"),
                FIXED_CLOCK,
            )
            val day = LocalDate.of(2026, 7, 11)
            val task = firstRepository.createTask(
                account.id,
                planner.id,
                TaskDraft("Enviar documentos", day, null),
            )
            val api = CanonicalSyncApi()
            val firstEngine = SyncEngine(database, api, SyncSession(account.id, "device-1"), FIXED_CLOCK)
            val secondEngine = SyncEngine(secondDatabase, api, SyncSession(account.id, "device-2"), FIXED_CLOCK)
            firstEngine.syncOnce()
            secondEngine.syncOnce()

            firstRepository.setTaskCompleted(account.id, task.id, completed = true)
            firstEngine.syncOnce()
            secondEngine.syncOnce()

            assertEquals(
                TaskStatus.DONE,
                secondRepository.observeDay(account.id, day).first().tasks.single().status,
            )
        } finally {
            secondDatabase.close()
        }
    }

    @Test
    fun `account timezone change is applied from incremental pull`() = runTest {
        val account = Account("account-1", "gui", "America/Sao_Paulo", false)
        val planner = Planner("planner-1", account.id)
        RoomSessionStateStore(database).saveActive(account, planner)
        val api = CanonicalSyncApi()
        val operation = SyncOperation(
            operationId = "timezone-operation",
            entityType = "account_settings",
            entityId = account.id,
            kind = "upsert",
            payloadJson = "{\"timezone\":\"Europe/Lisbon\"}",
            clientUpdatedAt = "2026-07-11T12:00:00Z",
        )
        api.push("token", listOf(operation))

        SyncEngine(database, api, SyncSession(account.id, "token"), FIXED_CLOCK).syncOnce()

        assertEquals("Europe/Lisbon", RoomSessionStateStore(database).readActive()?.first?.timezone)
    }

    private companion object {
        val FIXED_CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-07-11T12:00:00Z"),
            ZoneOffset.UTC,
        )
    }
}

private class QueueIdGenerator(vararg ids: String) : IdGenerator {
    private val values = ArrayDeque(ids.toList())
    override fun nextId(): String = values.removeFirst()
}

private class RecordingSyncApi : SyncApi {
    val receivedOperations = mutableListOf<SyncOperation>()

    override suspend fun push(token: String, operations: List<SyncOperation>): List<PushResult> {
        receivedOperations += operations
        return operations.map { PushResult(it.operationId, PushResultStatus.ACCEPTED, version = 1) }
    }

    override suspend fun pull(token: String, cursor: Long, limit: Int): PullPage =
        PullPage(emptyList(), nextCursor = cursor, hasMore = false)
}

private class CanonicalSyncApi : SyncApi {
    private val changes = mutableListOf<SyncChange>()
    val pullCursors = mutableListOf<Long>()

    override suspend fun push(token: String, operations: List<SyncOperation>): List<PushResult> =
        operations.map { operation ->
            val cursor = changes.size.toLong() + 1
            changes += SyncChange(
                cursor = cursor,
                operationId = operation.operationId,
                entityType = operation.entityType,
                entityId = operation.entityId,
                kind = operation.kind,
                payloadJson = operation.payloadJson,
                version = cursor,
                updatedAt = "2026-07-11T12:00:01Z",
            )
            PushResult(operation.operationId, PushResultStatus.ACCEPTED, version = cursor)
        }

    override suspend fun pull(token: String, cursor: Long, limit: Int): PullPage {
        pullCursors += cursor
        val page = changes.filter { it.cursor > cursor }.take(limit)
        return PullPage(
            changes = page,
            nextCursor = page.lastOrNull()?.cursor ?: cursor,
            hasMore = changes.count { it.cursor > cursor } > page.size,
        )
    }
}
