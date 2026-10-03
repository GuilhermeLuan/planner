package dev.guilhermeluan.planner.notifications

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import dev.guilhermeluan.planner.tasks.PlannerMedicine
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.testsupport.seed
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class MedicineReminderCoordinatorTest {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: RoomPlannerRepository
    private lateinit var coordinator: MedicineReminderCoordinator
    private val gateway = FakeDoseReminderGateway()
    private val account = Account("account-1", "ana", "America/Sao_Paulo", false)
    private val planner = Planner("planner-1", account.id)
    private val thursday = LocalDate.of(2026, 10, 1)

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
        coordinator = MedicineReminderCoordinator(repository, gateway, clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun create(
        name: String = "Vitamina D",
        times: Set<LocalTime> = setOf(LocalTime.of(13, 0)),
        repeat: MedicineRepeat = MedicineRepeat.Daily,
    ): PlannerMedicine {
        database.seed(account, planner)
        return repository.createMedicine(
            account.id,
            planner.id,
            MedicineDraft(name, 1, DoseUnit.CAPSULE, times, repeat, thursday),
        )
    }

    @Test
    fun aPendingDoseGetsAReminderAtItsTimeInTheAccountTimezone() = runTest {
        create("Vitamina D", setOf(LocalTime.of(13, 0)))

        coordinator.reconcile(account.id, account.timezone)

        val first = gateway.reminders.minBy { it.triggerAt }
        assertEquals(Instant.parse("2026-10-01T16:00:00Z"), first.triggerAt)
        assertEquals("Vitamina D", first.name)
        assertEquals("1 cápsula", first.dose)
    }

    @Test
    fun takenAndSkippedDosesLoseTheirReminder() = runTest {
        val medicine = create("Magnésio", setOf(LocalTime.of(13, 0), LocalTime.of(21, 30)))
        coordinator.reconcile(account.id, account.timezone)

        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(21, 30), DoseStatus.SKIPPED)
        coordinator.reconcile(account.id, account.timezone)

        assertTrue(gateway.reminders.none { it.day == thursday })
        assertEquals(LocalDate.of(2026, 10, 2), gateway.reminders.minBy { it.triggerAt }.day)
    }

    @Test
    fun aDoseRegisteredInTheAppDismissesItsDeliveredReminder() = runTest {
        val medicine = create("Vitamina D", setOf(LocalTime.of(8, 0), LocalTime.of(13, 0)))
        val taken = DoseReminder.keyOf(medicine.id, thursday, LocalTime.of(8, 0))
        val pending = DoseReminder.keyOf(medicine.id, thursday, LocalTime.of(13, 0))

        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(8, 0), DoseStatus.TAKEN)
        coordinator.reconcile(account.id, account.timezone)

        assertTrue(taken in gateway.dismissed)
        assertTrue(pending !in gateway.dismissed)
    }

    @Test
    fun anArchivedMedicineHasNoReminders() = runTest {
        val medicine = create()
        coordinator.reconcile(account.id, account.timezone)

        repository.archiveMedicine(account.id, medicine.id, thursday)
        coordinator.reconcile(account.id, account.timezone)

        assertEquals(emptyList<DoseReminder>(), gateway.reminders)
    }

    @Test
    fun aDoseWhoseTimeAlreadyPassedTodayGetsNoReminder() = runTest {
        create("Anticoncepcional", setOf(LocalTime.of(8, 0)))

        coordinator.reconcile(account.id, account.timezone)

        assertTrue(gateway.reminders.none { it.day == thursday })
    }

    @Test
    fun changingTheAccountTimezoneReschedulesTheReminders() = runTest {
        create("Vitamina D", setOf(LocalTime.of(13, 0)))
        coordinator.reconcile(account.id, account.timezone)

        // 08:04 em São Paulo já é 20:04 de quinta em Tóquio: a Dose de quinta passou.
        coordinator.reconcile(account.id, "Asia/Tokyo")

        val first = gateway.reminders.minBy { it.triggerAt }
        assertEquals(LocalDate.of(2026, 10, 2), first.day)
        assertEquals(Instant.parse("2026-10-02T04:00:00Z"), first.triggerAt)
    }

    @Test
    fun theTookItActionRegistersTheDoseAndDropsItsReminder() = runTest {
        create("Vitamina D", setOf(LocalTime.of(13, 0)))
        coordinator.reconcile(account.id, account.timezone)
        val reminder = gateway.reminders.minBy { it.triggerAt }
        now = Instant.parse("2026-10-01T16:02:00Z")

        coordinator.applyAction(account.id, account.timezone, reminder.key, DoseReminderAction.TAKE)

        val dose = repository.observeDoses(account.id, thursday).first().single()
        assertEquals(DoseStatus.TAKEN, dose.status)
        assertEquals(now, dose.takenAt)
        assertTrue(gateway.reminders.none { it.key == reminder.key })
    }

    @Test
    fun theTookItActionOnAnAlreadySettledDoseKeepsWhatWasRegistered() = runTest {
        val medicine = create("Vitamina D", setOf(LocalTime.of(13, 0), LocalTime.of(21, 30)))
        now = Instant.parse("2026-10-01T16:02:00Z")
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(21, 30), DoseStatus.SKIPPED)
        now = Instant.parse("2026-10-02T00:40:00Z")

        listOf(LocalTime.of(13, 0), LocalTime.of(21, 30)).forEach { time ->
            val key = DoseReminder.keyOf(medicine.id, thursday, time)
            coordinator.applyAction(account.id, account.timezone, key, DoseReminderAction.TAKE)
        }

        val doses = repository.observeDoses(account.id, thursday).first()
        assertEquals(listOf(DoseStatus.TAKEN, DoseStatus.SKIPPED), doses.map { it.status })
        assertEquals(Instant.parse("2026-10-01T16:02:00Z"), doses.first().takenAt)
    }

    @Test
    fun snoozingRemindsAgainTenMinutesLaterEvenAfterARebuild() = runTest {
        create("Vitamina D", setOf(LocalTime.of(13, 0)))
        coordinator.reconcile(account.id, account.timezone)
        val reminder = gateway.reminders.minBy { it.triggerAt }
        now = Instant.parse("2026-10-01T16:02:00Z")

        coordinator.applyAction(account.id, account.timezone, reminder.key, DoseReminderAction.SNOOZE)
        coordinator.reconcile(account.id, account.timezone)

        assertEquals(Instant.parse("2026-10-01T16:12:00Z"), gateway.reminders.single { it.key == reminder.key }.triggerAt)
        assertEquals(DoseStatus.PENDING, repository.observeDoses(account.id, thursday).first().single().status)
    }

    @Test
    fun snoozingBeforeTheDoseTimeNeverRemindsEarlier() = runTest {
        val medicine = create("Vitamina D", setOf(LocalTime.of(13, 0)))
        val key = DoseReminder.keyOf(medicine.id, thursday, LocalTime.of(13, 0))

        coordinator.applyAction(account.id, account.timezone, key, DoseReminderAction.SNOOZE)
        coordinator.applyAction(account.id, account.timezone, key, DoseReminderAction.SNOOZE)

        assertEquals(Instant.parse("2026-10-01T16:20:00Z"), gateway.reminders.single { it.key == key }.triggerAt)
    }
}

/** Gateway falso: guarda o que está agendado, como o AlarmManager guardaria. Seguro entre threads. */
class FakeDoseReminderGateway : DoseReminderGateway {
    private val scheduled = linkedMapOf<String, DoseReminder>()

    val reminders: List<DoseReminder> @Synchronized get() = scheduled.values.toList()

    /** Lembretes já entregues que foram tirados da tela. */
    val dismissed = mutableSetOf<String>()

    @Synchronized
    override fun scheduledKeys(): Set<String> = scheduled.keys.toSet()

    @Synchronized
    override fun schedule(reminder: DoseReminder) {
        scheduled[reminder.key] = reminder
    }

    @Synchronized
    override fun cancel(key: String) {
        scheduled.remove(key)
    }

    @Synchronized
    override fun dismiss(key: String) {
        dismissed += key
    }
}
