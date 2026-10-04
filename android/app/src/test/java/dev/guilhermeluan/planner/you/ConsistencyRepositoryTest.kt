package dev.guilhermeluan.planner.you

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.IdGenerator
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.tasks.RoutineDraft
import dev.guilhermeluan.planner.tasks.RoutineOccurrenceStatus
import dev.guilhermeluan.planner.tasks.TaskDraft
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ConsistencyRepositoryTest {
    private lateinit var database: PlannerDatabase
    private lateinit var planner: RoomPlannerRepository
    private lateinit var water: WaterRepository
    private lateinit var consistency: ConsistencyRepository
    private val account = Account("account-1", "ana", "America/Sao_Paulo", false)
    private val plannerRef = Planner("planner-1", account.id)
    private val zone = ZoneId.of("America/Sao_Paulo")

    // 01/10 01:30Z ainda é 30/09 22:30 no Fuso da Conta: o mês corrente é setembro.
    private var now = Instant.parse("2026-10-01T01:30:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
        override fun instant(): Instant = now
    }

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.seed(account, plannerRef)
        var next = 0
        planner = RoomPlannerRepository(database, IdGenerator { "id-${next++}" }, clock)
        water = WaterRepository(database, clock)
        consistency = ConsistencyRepository(database, planner, water, clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun current() = consistency.observe(account.id, zone).first()

    private suspend fun routineDone(day: String, status: RoutineOccurrenceStatus = RoutineOccurrenceStatus.DONE, routineId: String? = null): String {
        val id = routineId ?: planner.createRoutine(
            account.id, plannerRef.id,
            RoutineDraft("Alongar", DayOfWeek.values().toSet(), LocalDate.of(2026, 8, 1), null),
        ).id
        planner.setRoutineOccurrenceStatus(account.id, id, LocalDate.parse(day), status)
        return id
    }

    @Test
    fun routinesDoneCountsOnlyCompletedOccurrencesOfTheCurrentMonthInTheAccountZone() = runTest {
        val id = routineDone("2026-09-01")
        routineDone("2026-09-30", routineId = id)
        routineDone("2026-09-15", RoutineOccurrenceStatus.SKIPPED, routineId = id)
        routineDone("2026-08-31", routineId = id)
        routineDone("2026-10-01", routineId = id)

        assertEquals(2, current().routinesDoneThisMonth)
    }

    @Test
    fun waterStreakCountsDaysInARowThatMetTheGoalAcrossMonths() = runTest {
        listOf("2026-09-28", "2026-09-29", "2026-09-30").forEach { water.add(account.id, LocalDate.parse(it), 2000) }
        water.add(account.id, LocalDate.parse("2026-09-26"), 2500)
        water.adjustTotal(account.id, LocalDate.parse("2026-09-27"), 500)

        assertEquals(3, current().waterStreakDays)
    }

    @Test
    fun waterStreakIsZeroWithoutAnyConsumption() = runTest {
        assertEquals(0, current().waterStreakDays)
    }

    @Test
    fun waterStreakFollowsTheGoalThatAppliedOnEachDay() = runTest {
        water.setGoal(account.id, 1500, from = LocalDate.parse("2026-09-01"))
        water.add(account.id, LocalDate.parse("2026-09-29"), 1500)
        water.setGoal(account.id, 2000, from = LocalDate.parse("2026-09-30"))
        water.add(account.id, LocalDate.parse("2026-09-30"), 1500)

        // Hoje (meta 2.000) ainda não bateu, mas ontem (meta 1.500) sim.
        assertEquals(1, current().waterStreakDays)
    }

    @Test
    fun onTimeDosePercentCoversOnlyThisMonthsDosesAlreadyDue() = runTest {
        val medicine = planner.createMedicine(
            account.id, plannerRef.id,
            MedicineDraft(
                "Losartana", 1, DoseUnit.TABLET, setOf(LocalTime.of(8, 0)),
                MedicineRepeat.Period(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 10, 1)), LocalDate.of(2026, 8, 31),
            ),
        )
        take(medicine.id, "2026-08-31", at = "2026-08-31T13:00:00Z") // atrasada e do mês passado
        take(medicine.id, "2026-09-01", at = "2026-09-01T11:10:00Z") // 08:10: no horário
        take(medicine.id, "2026-09-02", at = "2026-09-02T13:00:00Z") // 10:00: atrasada
        planner.setDoseStatus(account.id, medicine.id, LocalDate.of(2026, 9, 3), LocalTime.of(8, 0), DoseStatus.SKIPPED)
        now = Instant.parse("2026-10-01T01:30:00Z")

        // Setembro tem 30 Doses vencidas; só a do dia 1 foi tomada no horário.
        assertEquals(Math.round(100f / 30), current().onTimeDosePercent)
    }

    @Test
    fun aDoseTakenWithinTheAlarmDelayCountsAsOnTimeForAMedicineWithAlarm() = runTest {
        fun medicine(name: String, alarm: Int?) = MedicineDraft(
            name, 1, DoseUnit.TABLET, setOf(LocalTime.of(8, 0)),
            MedicineRepeat.Period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)), LocalDate.of(2026, 9, 1),
            alarmDelayMinutes = alarm,
        )
        val withAlarm = planner.createMedicine(account.id, plannerRef.id, medicine("Magnésio", 60))
        val withoutAlarm = planner.createMedicine(account.id, plannerRef.id, medicine("Vitamina D", null))
        // 08:45: dentro da hora do alarme, mas passou dos 30 minutos de tolerância sem alarme.
        take(withAlarm.id, "2026-09-01", at = "2026-09-01T11:45:00Z")
        take(withoutAlarm.id, "2026-09-01", at = "2026-09-01T11:45:00Z")

        assertEquals(50, current().onTimeDosePercent)
    }

    @Test
    fun withoutDosesThereIsNoPercentage() = runTest {
        assertNull(current().onTimeDosePercent)
    }

    @Test
    fun theMonthRollsOverWhenTheAccountZoneReachesTheNextMonth() = runTest {
        routineDone("2026-09-30")
        assertEquals(1, current().routinesDoneThisMonth)

        now = Instant.parse("2026-10-01T03:30:00Z") // 00:30 de 01/10 em São Paulo

        assertEquals(0, consistency.observe(account.id, zone).first().routinesDoneThisMonth)
    }

    @Test
    fun memberSinceIsTheMonthOfTheOldestThingInThePlanner() = runTest {
        planner.createTask(account.id, plannerRef.id, TaskDraft("Pagar conta", LocalDate.of(2026, 7, 14), null))

        assertEquals(YearMonth.of(2026, 7), consistency.memberSince(account.id, zone))
    }

    @Test
    fun memberSinceAlsoLooksAtWaterAndMedicines() = runTest {
        water.add(account.id, LocalDate.of(2026, 5, 3), 200)
        planner.createMedicine(
            account.id, plannerRef.id,
            MedicineDraft("Vitamina D", 1, DoseUnit.CAPSULE, setOf(LocalTime.of(8, 0)), MedicineRepeat.Daily, LocalDate.of(2026, 6, 20)),
        )

        assertEquals(YearMonth.of(2026, 5), consistency.memberSince(account.id, zone))
    }

    @Test
    fun memberSinceIsRememberedOnceDiscovered() = runTest {
        assertEquals(YearMonth.of(2026, 9), consistency.memberSince(account.id, zone))

        now = Instant.parse("2026-12-10T12:00:00Z")

        assertEquals(YearMonth.of(2026, 9), consistency.memberSince(account.id, zone))
    }

    private suspend fun take(medicineId: String, day: String, at: String) {
        now = Instant.parse(at)
        planner.setDoseStatus(account.id, medicineId, LocalDate.parse(day), LocalTime.of(8, 0), DoseStatus.TAKEN)
    }
}
