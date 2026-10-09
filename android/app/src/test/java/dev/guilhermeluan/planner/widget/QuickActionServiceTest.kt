package dev.guilhermeluan.planner.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.notifications.DoseAlarmGateway
import dev.guilhermeluan.planner.notifications.DoseAlarm
import dev.guilhermeluan.planner.notifications.DoseReminder
import dev.guilhermeluan.planner.notifications.DoseReminderGateway
import dev.guilhermeluan.planner.notifications.DoseScheduleCoordinator
import dev.guilhermeluan.planner.notifications.WaterReminder
import dev.guilhermeluan.planner.notifications.WaterReminderCoordinator
import dev.guilhermeluan.planner.notifications.WaterReminderGateway
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.tasks.DoseKey
import dev.guilhermeluan.planner.tasks.IdGenerator
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.testsupport.seed
import dev.guilhermeluan.planner.water.WaterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class QuickActionServiceTest {
    private lateinit var database: PlannerDatabase
    private lateinit var water: WaterRepository
    private lateinit var service: QuickActionService
    private val account = Account("account-1", "ana", "America/Sao_Paulo", false)
    private val planner = Planner("planner-1", account.id)

    // 09:00 de quinta, 8/10, em São Paulo.
    private val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.seed(account, planner)
        water = WaterRepository(database, clock)
        val medicines = RoomPlannerRepository(database, IdGenerator { "id" }, clock)
        val reminders = WaterReminderCoordinator(water, object : WaterReminderGateway {
            override fun schedule(reminder: WaterReminder) = Unit
            override fun cancel() = Unit
            override fun dismiss() = Unit
        }, clock)
        val doseReminders = object : DoseReminderGateway {
            override fun scheduledKeys() = emptySet<DoseKey>()
            override fun schedule(reminder: DoseReminder) = Unit
            override fun cancel(key: DoseKey) = Unit
            override fun dismiss(key: DoseKey) = Unit
        }
        val doseAlarms = object : DoseAlarmGateway {
            override fun canScheduleExactAlarms() = false
            override fun scheduledKeys() = emptySet<DoseKey>()
            override fun schedule(alarm: DoseAlarm) = Unit
            override fun cancel(key: DoseKey) = Unit
            override fun dismiss(key: DoseKey) = Unit
        }
        service = QuickActionService(water, reminders, medicines, DoseScheduleCoordinator(medicines, doseReminders, doseAlarms, clock), clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `agua soma o copo padrao e confirma com o total do dia por 3 segundos`() = runTest {
        service.addWater(account.id, account.timezone)
        val second = service.addWater(account.id, account.timezone)

        assertEquals(400, water.observeDay(account.id, LocalDate.of(2026, 10, 8)).first().consumedMl)
        assertEquals("+200 ml", second.title)
        assertEquals("0,4 L hoje", second.detail)
        assertEquals(clock.instant().plusSeconds(3), second.until)
    }

    @Test
    fun `remedio sem dose pendente hoje nao marca nem confirma`() = runTest {
        assertNull(service.takeNextDose(account.id, account.timezone))
    }
}
