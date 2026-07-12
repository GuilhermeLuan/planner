package dev.guilhermeluan.planner.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.storage.PlannerDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomSessionStateStoreTest {
    private lateinit var database: PlannerDatabase
    private lateinit var store: RoomSessionStateStore

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = RoomSessionStateStore(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `only the explicitly active account is exposed and logout preserves separation`() = runTest {
        val first = Account("account-1", "gui", "America/Sao_Paulo", false)
        val firstPlanner = Planner("planner-1", first.id)
        val second = Account("account-2", "ana", "America/Fortaleza", false)
        val secondPlanner = Planner("planner-2", second.id)

        store.saveActive(first, firstPlanner)
        store.saveActive(second, secondPlanner)
        assertEquals(second to secondPlanner, store.readActive())

        store.clearActiveAccess()
        assertNull(store.readActive())

        store.saveActive(first, firstPlanner)
        assertEquals(first to firstPlanner, store.readActive())
    }
}
