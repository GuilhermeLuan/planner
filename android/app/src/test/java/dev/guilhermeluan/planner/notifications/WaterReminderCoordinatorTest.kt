package dev.guilhermeluan.planner.notifications

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.WaterReminderSettingsEntity
import dev.guilhermeluan.planner.testsupport.seed
import dev.guilhermeluan.planner.water.WaterDay
import dev.guilhermeluan.planner.water.WaterRepository
import dev.guilhermeluan.planner.water.WaterReminderSettings
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WaterReminderCoordinatorTest {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: WaterRepository
    private lateinit var coordinator: WaterReminderCoordinator
    private val gateway = FakeWaterReminderGateway()
    private val account = Account("account-1", "ana", "America/Sao_Paulo", false)
    private val planner = Planner("planner-1", account.id)
    private val thursday = LocalDate.of(2026, 10, 1)

    // 08:04 de quinta, 1/10, em São Paulo.
    private var now = Instant.parse("2026-10-01T11:04:00Z")

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.seed(account, planner)
        val clock = clockIn(ZoneOffset.UTC)
        repository = WaterRepository(database, clock)
        coordinator = WaterReminderCoordinator(repository, gateway, clock)
    }

    @After
    fun tearDown() = database.close()

    /** Relógio que lê [now]; o fuso pedido vale para a data de hoje, como no relógio do sistema. */
    private fun clockIn(zone: ZoneId): Clock = object : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = clockIn(zone)
        override fun instant(): Instant = now
    }

    private suspend fun saveSettings(
        enabled: Boolean = true,
        intervalHours: Int = 2,
        windowStart: LocalTime = LocalTime.of(8, 0),
        windowEnd: LocalTime = LocalTime.of(20, 0),
    ) = repository.saveReminderSettings(account.id, WaterReminderSettings(enabled, intervalHours, windowStart, windowEnd))

    /** Reconcilia com o relógio em [at] e devolve o Lembrete agendado. */
    private suspend fun reconcileAt(at: String): WaterReminder? {
        now = Instant.parse(at)
        coordinator.reconcile(account.id, account.timezone)
        return gateway.reminder
    }

    @Test
    fun aDisabledReminderCancelsWhatWasScheduledAndDismissesTheDeliveredOne() = runTest {
        gateway.reminder = WaterReminder(Instant.parse("2026-10-01T13:00:00Z"))
        saveSettings(enabled = false)

        coordinator.reconcile(account.id, account.timezone)

        assertNull(gateway.reminder)
        assertEquals(1, gateway.dismissals)
    }

    @Test
    fun theRemindersOfTheDayStepByTheIntervalFromTheWindowStartUpToTheWindowEndInclusive() = runTest {
        saveSettings(intervalHours = 3, windowStart = LocalTime.of(8, 0), windowEnd = LocalTime.of(20, 0))

        // Em São Paulo os horários são 08:00, 11:00, 14:00, 17:00 e 20:00 (fim da janela, inclusive).
        // Cada linha leva o relógio até um horário e confere o seguinte.
        assertEquals(WaterReminder(Instant.parse("2026-10-01T11:00:00Z")), reconcileAt("2026-10-01T10:59:00Z"))
        assertEquals(WaterReminder(Instant.parse("2026-10-01T14:00:00Z")), reconcileAt("2026-10-01T11:00:00Z"))
        assertEquals(WaterReminder(Instant.parse("2026-10-01T17:00:00Z")), reconcileAt("2026-10-01T14:00:00Z"))
        assertEquals(WaterReminder(Instant.parse("2026-10-01T20:00:00Z")), reconcileAt("2026-10-01T17:00:00Z"))
        assertEquals(WaterReminder(Instant.parse("2026-10-01T23:00:00Z")), reconcileAt("2026-10-01T20:00:00Z"))
    }

    @Test
    fun theNextReminderIsTheFirstTimeAfterNowOnTheAccountTimezone() = runTest {
        saveSettings(intervalHours = 2)

        // 08:04 em São Paulo: o próximo horário, de 2 em 2 horas, é 10:00 de São Paulo (13:00 UTC).
        coordinator.reconcile(account.id, account.timezone)

        assertEquals(WaterReminder(Instant.parse("2026-10-01T13:00:00Z")), gateway.reminder)
    }

    @Test
    fun whenTodaysLastTimeHasPassedTheNextReminderIsTomorrowsFirstTime() = runTest {
        saveSettings(intervalHours = 3, windowStart = LocalTime.of(9, 0), windowEnd = LocalTime.of(13, 0))

        // De 3 em 3 horas, das 09:00 às 13:00, sobram 09:00 e 12:00. Às 12:30 de São Paulo, nenhum vale mais.
        assertEquals(WaterReminder(Instant.parse("2026-10-02T12:00:00Z")), reconcileAt("2026-10-01T15:30:00Z"))
    }

    @Test
    fun whenTheGoalIsMetTodaysRemindersAreDroppedAndTheDeliveredOneIsDismissed() = runTest {
        saveSettings(intervalHours = 2)
        repository.add(account.id, thursday, WaterRepository.DEFAULT_GOAL_ML)

        // 08:04 em São Paulo com a meta de 2 L batida: nenhum horário de hoje vale, o próximo é 08:00 de amanhã.
        coordinator.reconcile(account.id, account.timezone)

        assertEquals(WaterReminder(Instant.parse("2026-10-02T11:00:00Z")), gateway.reminder)
        assertEquals(1, gateway.dismissals)
    }

    @Test
    fun changingTheTimezoneReschedulesToTheLocalTimeOfTheNewZone() = runTest {
        saveSettings(intervalHours = 2)
        coordinator.reconcile(account.id, account.timezone)
        assertEquals(WaterReminder(Instant.parse("2026-10-01T13:00:00Z")), gateway.reminder)

        // Os mesmos 08:04 de São Paulo são 20:04 de quinta em Tóquio: os horários de hoje já passaram.
        coordinator.reconcile(account.id, "Asia/Tokyo")

        assertEquals(WaterReminder(Instant.parse("2026-10-01T23:00:00Z")), gateway.reminder)
    }

    @Test
    fun addingAGlassAddsToTodayInTheTimezoneAndMeetingTheGoalMovesTheReminderToTomorrow() = runTest {
        saveSettings(intervalHours = 2, windowStart = LocalTime.of(21, 0), windowEnd = LocalTime.of(23, 0))
        // 21:30 de quinta em São Paulo, mas já é sexta em UTC: o copo entra em quinta.
        now = Instant.parse("2026-10-02T00:30:00Z")
        repository.add(account.id, thursday, 1800)

        coordinator.addGlass(account.id, account.timezone)

        assertEquals(2000, repository.observeDay(account.id, thursday).first().consumedMl)
        assertEquals(0, repository.observeDay(account.id, thursday.plusDays(1)).first().consumedMl)
        // Meta batida: nenhum horário de hoje vale, e o próximo é 21:00 de sexta.
        assertEquals(WaterReminder(Instant.parse("2026-10-03T00:00:00Z")), gateway.reminder)
    }

    @Test
    fun theConsumptionOfTodayIsDueOnlyWithTheReminderOnAndTheGoalNotMet() = runTest {
        saveSettings(enabled = true)
        repository.add(account.id, thursday, 200)
        assertEquals(WaterDay(thursday, 200, 2000), coordinator.dueReminder(account.id, account.timezone))

        // Meta padrão de 2 L batida: não há mais o que lembrar.
        repository.add(account.id, thursday, 1800)
        assertNull(coordinator.dueReminder(account.id, account.timezone))

        saveSettings(enabled = false)
        repository.adjustTotal(account.id, thursday, 200)
        assertNull(coordinator.dueReminder(account.id, account.timezone))
    }

    @Test
    fun changingTheIntervalAndReconcilingReplacesTheScheduledReminder() = runTest {
        saveSettings(intervalHours = 2)
        coordinator.reconcile(account.id, account.timezone)
        assertEquals(WaterReminder(Instant.parse("2026-10-01T13:00:00Z")), gateway.reminder)

        // De 2 em 2 horas para de 1 em 1: às 08:04 o próximo passa a ser 09:00 de São Paulo.
        saveSettings(intervalHours = 1)
        coordinator.reconcile(account.id, account.timezone)

        assertEquals(WaterReminder(Instant.parse("2026-10-01T12:00:00Z")), gateway.reminder)
    }

    @Test
    fun addingAGlassDoesNotDeadlockOnTheReconcileItCalls() = runTest {
        saveSettings(intervalHours = 2)

        // addGlass chama reconcile: se o lock de reconcile estivesse em volta de addGlass, a corrotina travaria aqui.
        coordinator.addGlass(account.id, account.timezone)

        assertEquals(WaterReminder(Instant.parse("2026-10-01T13:00:00Z")), gateway.reminder)
    }

    @Test
    fun anInvalidReminderSettingsRowIsTreatedAsOff() = runTest {
        // O repositório recusa uma janela que termina antes de começar, então a linha entra direto pelo DAO.
        database.waterDao().upsertReminderSettings(WaterReminderSettingsEntity(account.id, true, 2, "20:00", "08:00"))
        gateway.reminder = WaterReminder(Instant.parse("2026-10-01T13:00:00Z"))

        coordinator.reconcile(account.id, account.timezone)

        assertNull(gateway.reminder)
        assertEquals(1, gateway.dismissals)
    }

    @Test
    fun anInvalidReminderSettingsRowHasNoConsumptionDue() = runTest {
        database.waterDao().upsertReminderSettings(WaterReminderSettingsEntity(account.id, true, 2, "20:00", "08:00"))
        repository.add(account.id, thursday, 200)

        assertNull(coordinator.dueReminder(account.id, account.timezone))
    }
}

/** Gateway falso: guarda o Lembrete agendado, como o AlarmManager guardaria. Seguro entre threads. */
class FakeWaterReminderGateway : WaterReminderGateway {
    var reminder: WaterReminder? = null

    /** Vezes que o Lembrete entregue foi tirado da tela. */
    var dismissals = 0

    @Synchronized
    override fun schedule(reminder: WaterReminder) {
        this.reminder = reminder
    }

    @Synchronized
    override fun cancel() {
        reminder = null
    }

    @Synchronized
    override fun dismiss() {
        dismissals++
    }
}
