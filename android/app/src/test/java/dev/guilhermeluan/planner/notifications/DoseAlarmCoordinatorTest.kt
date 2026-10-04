package dev.guilhermeluan.planner.notifications

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.tasks.DoseKey
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import dev.guilhermeluan.planner.tasks.PlannerMedicine
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.testsupport.seed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** O Alarme de Dose no coordenador: Magnésio às 21:30 de quinta, com alarme 30 min depois (22:00, 01:00Z). */
@RunWith(RobolectricTestRunner::class)
class DoseAlarmCoordinatorTest {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: RoomPlannerRepository
    private lateinit var coordinator: DoseScheduleCoordinator
    private val reminders = FakeDoseReminderGateway()
    private val alarms = FakeDoseAlarmGateway()
    private val account = Account("account-1", "ana", "America/Sao_Paulo", false)
    private val planner = Planner("planner-1", account.id)
    private val thursday = LocalDate.of(2026, 10, 1)
    private val night = LocalTime.of(21, 30)
    private val alarmTime = Instant.parse("2026-10-02T01:00:00Z")

    // 08:04 de quinta, 1/10, em São Paulo.
    private var now = Instant.parse("2026-10-01T11:04:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId) = this
            override fun instant(): Instant = now
        }
        var next = 0
        repository = RoomPlannerRepository(database, { "id-${next++}" }, clock)
        coordinator = DoseScheduleCoordinator(repository, reminders, alarms, clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun magnesium(alarmDelayMinutes: Int? = 30): PlannerMedicine {
        database.seed(account, planner)
        return repository.createMedicine(
            account.id,
            planner.id,
            MedicineDraft("Magnésio", 1, DoseUnit.CAPSULE, setOf(night), MedicineRepeat.Daily, thursday, alarmDelayMinutes = alarmDelayMinutes),
        )
    }

    private fun PlannerMedicine.key() = DoseKey(id, thursday, night)

    private fun PlannerMedicine.alarm() = alarms.alarms.singleOrNull { it.key == key() }

    private suspend fun reconcile(timezone: String = account.timezone) = coordinator.reconcile(account.id, timezone)

    private suspend fun answer(medicine: PlannerMedicine, action: DoseAlarmAction) =
        coordinator.applyAlarmAction(account.id, account.timezone, medicine.key(), action)

    private suspend fun setStatus(medicine: PlannerMedicine, status: DoseStatus) =
        repository.setDoseStatus(account.id, medicine.id, thursday, night, status)

    private suspend fun dose() = repository.observeDoses(account.id, thursday).first().single()

    /** O Magnésio agendado e o relógio no horário do alarme. */
    private suspend fun ringing(): PlannerMedicine {
        val medicine = magnesium()
        reconcile()
        now = alarmTime
        return medicine
    }

    @Test
    fun aMedicineWithAnAlarmGetsADoseAlarmAtTheTimePlusTheDelay() = runTest {
        val medicine = magnesium()

        reconcile()

        val alarm = medicine.alarm()!!
        assertEquals(alarmTime, alarm.triggerAt)
        assertEquals("Magnésio", alarm.name)
        assertEquals("1 cápsula", alarm.dose)
        assertEquals(night, alarm.key.time)
        assertEquals("o Lembrete segue no horário", Instant.parse("2026-10-02T00:30:00Z"), reminders.reminders.first { it.day == thursday }.triggerAt)
    }

    @Test
    fun aMedicineWithoutAnAlarmGetsNoDoseAlarm() = runTest {
        magnesium(alarmDelayMinutes = null)

        reconcile()

        assertEquals(emptyList<DoseAlarm>(), alarms.alarms)
    }

    @Test
    fun registeringTheDoseBeforeTheAlarmCancelsIt() = runTest {
        val medicine = magnesium()
        reconcile()

        setStatus(medicine, DoseStatus.TAKEN)
        reconcile()

        assertNull(medicine.alarm())
        assertTrue(medicine.key() in alarms.dismissed)
    }

    @Test
    fun skippingTheDoseAlsoCancelsTheAlarm() = runTest {
        val medicine = magnesium()
        reconcile()

        setStatus(medicine, DoseStatus.SKIPPED)
        reconcile()

        assertNull(medicine.alarm())
    }

    @Test
    fun snoozingTheReminderDoesNotPushTheAlarm() = runTest {
        val medicine = magnesium()
        now = Instant.parse("2026-10-02T00:31:00Z")

        coordinator.applyAction(account.id, account.timezone, medicine.key(), DoseReminderAction.SNOOZE)

        assertEquals(Instant.parse("2026-10-02T00:41:00Z"), reminders.reminders.single { it.key == medicine.key() }.triggerAt)
        assertEquals("o alarme segue em horário + atraso", alarmTime, medicine.alarm()!!.triggerAt)
    }

    @Test
    fun withoutTheExactAlarmPermissionThereIsNoDoseAlarmButTheReminderStays() = runTest {
        val medicine = magnesium()
        alarms.canScheduleExact = false

        reconcile()

        assertEquals(emptyList<DoseAlarm>(), alarms.alarms)
        assertEquals(1, reminders.reminders.count { it.key == medicine.key() })
    }

    @Test
    fun whenThePermissionIsGrantedReconcilingSchedulesTheAlarms() = runTest {
        val medicine = magnesium()
        alarms.canScheduleExact = false
        reconcile()

        alarms.canScheduleExact = true
        reconcile()

        assertNotNull(medicine.alarm())
    }

    @Test
    fun revokingThePermissionDropsTheScheduledAlarms() = runTest {
        val medicine = magnesium()
        reconcile()

        alarms.canScheduleExact = false
        reconcile()

        assertNull(medicine.alarm())
    }

    @Test
    fun anArchivedMedicineHasNoDoseAlarms() = runTest {
        val medicine = magnesium()
        reconcile()

        repository.archiveMedicine(account.id, medicine.id, thursday)
        reconcile()

        assertEquals(emptyList<DoseAlarm>(), alarms.alarms)
    }

    @Test
    fun changingTheAccountTimezoneReschedulesTheAlarm() = runTest {
        val medicine = magnesium()
        reconcile()

        reconcile("America/New_York")

        // 22:00 em Nova York (UTC-4) já é 23:00 em São Paulo.
        assertEquals(Instant.parse("2026-10-02T02:00:00Z"), medicine.alarm()!!.triggerAt)
    }

    @Test
    fun snoozingTheAlarmRingsAgainTenMinutesLaterAndKeepsTheDosePending() = runTest {
        val medicine = ringing()

        answer(medicine, DoseAlarmAction.SNOOZE)

        assertEquals(Instant.parse("2026-10-02T01:10:00Z"), medicine.alarm()!!.triggerAt)
        assertEquals(DoseStatus.PENDING, dose().status)
        assertTrue(medicine.key() in alarms.dismissed)
    }

    @Test
    fun theSnoozedAlarmSurvivesARebuild() = runTest {
        val medicine = ringing()
        answer(medicine, DoseAlarmAction.SNOOZE)

        reconcile()

        assertEquals(Instant.parse("2026-10-02T01:10:00Z"), medicine.alarm()!!.triggerAt)
    }

    @Test
    fun registeringTheDoseForgetsTheAlarmSnooze() = runTest {
        val medicine = ringing()
        answer(medicine, DoseAlarmAction.SNOOZE)

        setStatus(medicine, DoseStatus.TAKEN)
        setStatus(medicine, DoseStatus.PENDING)

        assertNull(dose().alarmSnoozedUntil)
    }

    @Test
    fun theTookItActionOnTheAlarmRegistersTheDoseAndStopsTheAlarm() = runTest {
        val medicine = ringing()

        answer(medicine, DoseAlarmAction.TAKE)

        assertEquals(DoseStatus.TAKEN, dose().status)
        assertNull(medicine.alarm())
        assertTrue(medicine.key() in alarms.dismissed)
    }

    @Test
    fun theSkipActionOnTheAlarmSkipsTheDoseAndStopsTheAlarm() = runTest {
        val medicine = ringing()

        answer(medicine, DoseAlarmAction.SKIP)

        assertEquals(DoseStatus.SKIPPED, dose().status)
        assertNull(medicine.alarm())
    }

    @Test
    fun theAlarmRingsWithTheDoseAndTheClockInTheAccountTimezone() = runTest {
        val medicine = ringing()

        val alarm = coordinator.alarmToRing(account.id, account.timezone, medicine.key())!!

        assertEquals(RingingDoseAlarm(medicine.key(), "Magnésio", "1 cápsula", LocalDateTime.of(2026, 10, 1, 22, 0)), alarm)
    }

    @Test
    fun theAlarmOnlyRingsWhileTheDoseIsPending() = runTest {
        val medicine = ringing()
        setStatus(medicine, DoseStatus.TAKEN)

        assertNull(coordinator.alarmToRing(account.id, account.timezone, medicine.key()))
    }

    @Test
    fun anAlarmSnoozedToTheFutureDoesNotRingNow() = runTest {
        val medicine = ringing()
        answer(medicine, DoseAlarmAction.SNOOZE)

        assertNull(coordinator.alarmToRing(account.id, account.timezone, medicine.key()))
    }
}

/** Gateway falso dos Alarmes de Dose: guarda o que está agendado e o que foi parado. Seguro entre threads. */
class FakeDoseAlarmGateway : DoseAlarmGateway {
    @Volatile
    var canScheduleExact = true
    private val scheduled = linkedMapOf<DoseKey, DoseAlarm>()

    val alarms: List<DoseAlarm> @Synchronized get() = scheduled.values.toList()

    /** Alarmes que tocavam ou estavam na tela e foram parados. */
    val dismissed: MutableSet<DoseKey> = java.util.Collections.synchronizedSet(mutableSetOf())

    override fun canScheduleExactAlarms(): Boolean = canScheduleExact

    @Synchronized
    override fun scheduledKeys(): Set<DoseKey> = scheduled.keys.toSet()

    @Synchronized
    override fun schedule(alarm: DoseAlarm) {
        scheduled[alarm.key] = alarm
    }

    @Synchronized
    override fun cancel(key: DoseKey) {
        scheduled.remove(key)
    }

    override fun dismiss(key: DoseKey) {
        dismissed += key
    }
}
