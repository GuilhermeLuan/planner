package dev.guilhermeluan.planner.you

import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.water.WaterDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ConsistencyTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val day = LocalDate.of(2026, 9, 10)

    private fun dose(time: String, status: DoseStatus = DoseStatus.PENDING, takenAt: String? = null, on: LocalDate = day) =
        PlannedDose("m1", "Losartana", 1, DoseUnit.TABLET, on, LocalTime.parse(time), status, takenAt?.let(Instant::parse))

    // 08:00 em São Paulo (UTC-3) é 11:00Z.
    private val afterEverything = Instant.parse("2026-09-30T12:00:00Z")

    @Test
    fun withoutAlarmADoseTakenUpTo30MinutesAfterTheReminderIsOnTime() {
        assertEquals(true, DoseTimeliness.isOnTime(dose("08:00", DoseStatus.TAKEN, "2026-09-10T11:30:00Z"), zone, alarmDelay = null))
        assertEquals(false, DoseTimeliness.isOnTime(dose("08:00", DoseStatus.TAKEN, "2026-09-10T11:31:00Z"), zone, alarmDelay = null))
    }

    @Test
    fun withAlarmADoseTakenUpToTheAlarmIsOnTime() {
        val delay = Duration.ofMinutes(60)
        assertEquals(true, DoseTimeliness.isOnTime(dose("08:00", DoseStatus.TAKEN, "2026-09-10T12:00:00Z"), zone, delay))
        assertEquals(false, DoseTimeliness.isOnTime(dose("08:00", DoseStatus.TAKEN, "2026-09-10T12:01:00Z"), zone, delay))
    }

    @Test
    fun theScheduledTimeIsReadInTheAccountZone() {
        // 08:00 em Manaus (UTC-4) é 12:00Z; tomar às 11:50Z é antes do horário.
        val manaus = ZoneId.of("America/Manaus")
        assertEquals(true, DoseTimeliness.isOnTime(dose("08:00", DoseStatus.TAKEN, "2026-09-10T11:50:00Z"), manaus, null))
        assertEquals(false, DoseTimeliness.isOnTime(dose("08:00", DoseStatus.TAKEN, "2026-09-10T13:00:00Z"), manaus, null))
    }

    @Test
    fun skippedAndPendingDosesAreNeverOnTime() {
        assertEquals(false, DoseTimeliness.isOnTime(dose("08:00", DoseStatus.SKIPPED), zone, null))
        assertEquals(false, DoseTimeliness.isOnTime(dose("08:00"), zone, null))
    }

    @Test
    fun percentageIsOnTimeDosesOverDosesAlreadyDue() {
        val doses = listOf(
            dose("08:00", DoseStatus.TAKEN, "2026-09-10T11:05:00Z"),
            dose("12:00", DoseStatus.TAKEN, "2026-09-10T15:10:00Z"),
            dose("20:00", DoseStatus.TAKEN, "2026-09-10T23:50:00Z"),
            dose("22:00", DoseStatus.SKIPPED),
        )
        assertEquals(50, DoseTimeliness.onTimePercent(doses, afterEverything, zone) { null })
    }

    @Test
    fun dosesThatAreNotDueYetDoNotCountAgainstThePerson() {
        val now = Instant.parse("2026-09-30T14:00:00Z") // 11:00 em São Paulo
        val doses = listOf(
            dose("08:00", DoseStatus.TAKEN, "2026-09-30T11:02:00Z", on = LocalDate.of(2026, 9, 30)),
            dose("21:00", on = LocalDate.of(2026, 9, 30)),
        )
        assertEquals(100, DoseTimeliness.onTimePercent(doses, now, zone) { null })
    }

    @Test
    fun percentageRoundsToTheNearestWholeNumber() {
        val doses = listOf(
            dose("08:00", DoseStatus.TAKEN, "2026-09-10T11:00:00Z"),
            dose("12:00", DoseStatus.TAKEN, "2026-09-10T15:00:00Z"),
            dose("20:00"),
        )
        assertEquals(67, DoseTimeliness.onTimePercent(doses, afterEverything, zone) { null })
    }

    @Test
    fun withoutDosesDueThereIsNoPercentage() {
        assertNull(DoseTimeliness.onTimePercent(emptyList(), afterEverything, zone) { null })
    }

    @Test
    fun eachDoseUsesTheAlarmDelayOfItsOwnMedicine() {
        val late = dose("08:00", DoseStatus.TAKEN, "2026-09-10T11:50:00Z")
        assertEquals(100, DoseTimeliness.onTimePercent(listOf(late), afterEverything, zone) { Duration.ofMinutes(60) })
        assertEquals(0, DoseTimeliness.onTimePercent(listOf(late), afterEverything, zone) { null })
    }

    private fun water(day: String, ml: Int, goal: Int = 2000) = WaterDay(LocalDate.parse(day), ml, goal)

    @Test
    fun waterStreakCountsConsecutiveDaysMeetingTheGoalUpToToday() {
        val days = listOf(water("2026-09-28", 2000), water("2026-09-29", 2500), water("2026-09-30", 2000))
        assertEquals(3, Consistency.waterStreak(days, today = LocalDate.parse("2026-09-30")))
    }

    @Test
    fun waterStreakStopsAtTheFirstDayThatMissedTheGoal() {
        val days = listOf(water("2026-09-27", 2000), water("2026-09-28", 900), water("2026-09-29", 2000), water("2026-09-30", 2000))
        assertEquals(2, Consistency.waterStreak(days, today = LocalDate.parse("2026-09-30")))
    }

    @Test
    fun waterStreakIsNotBrokenByTodayStillInProgress() {
        val days = listOf(water("2026-09-28", 2000), water("2026-09-29", 2000), water("2026-09-30", 300))
        assertEquals(2, Consistency.waterStreak(days, today = LocalDate.parse("2026-09-30")))
    }

    @Test
    fun waterStreakUsesTheGoalOfEachDay() {
        val days = listOf(water("2026-09-29", 1500, goal = 1500), water("2026-09-30", 1500, goal = 2000))
        assertEquals(1, Consistency.waterStreak(days, today = LocalDate.parse("2026-09-30")))
    }

    @Test
    fun aGapBreaksTheStreakEvenWhenAnOlderRunWasLonger() {
        val days = listOf(water("2026-09-20", 2000), water("2026-09-21", 2000), water("2026-09-22", 2000), water("2026-09-30", 2000))
        assertEquals(1, Consistency.waterStreak(days, today = LocalDate.parse("2026-09-30")))
    }

    @Test
    fun noDaysMeansNoStreak() {
        assertEquals(0, Consistency.waterStreak(emptyList(), today = LocalDate.parse("2026-09-30")))
    }
}
