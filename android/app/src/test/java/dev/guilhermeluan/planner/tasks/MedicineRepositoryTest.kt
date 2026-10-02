package dev.guilhermeluan.planner.tasks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.testsupport.seed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class MedicineRepositoryTest {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: RoomPlannerRepository
    private val account = Account("account-1", "ana", "America/Sao_Paulo", false)
    private val planner = Planner("planner-1", account.id)
    private val thursday = LocalDate.of(2026, 10, 1)
    private var now = Instant.parse("2026-10-01T11:04:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        var next = 0
        repository = RoomPlannerRepository(
            database,
            { "id-${next++}" },
            object : Clock() {
                override fun getZone() = ZoneOffset.UTC
                override fun withZone(zone: ZoneId) = this
                override fun instant(): Instant = now
            },
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun create(
        name: String = "Vitamina D",
        times: Set<LocalTime> = setOf(LocalTime.of(13, 0)),
        repeat: MedicineRepeat = MedicineRepeat.Daily,
        startDate: LocalDate = thursday,
        amount: Int = 1,
        stock: Int? = null,
        threshold: Int? = null,
    ): PlannerMedicine {
        database.seed(account, planner)
        return repository.createMedicine(
            account.id,
            planner.id,
            MedicineDraft(name, amount, DoseUnit.CAPSULE, times, repeat, startDate, stock, threshold),
        )
    }

    private suspend fun stockOf(medicine: PlannerMedicine) =
        repository.observeMedicines(account.id).first().single { it.id == medicine.id }.stock?.amount

    private suspend fun doses(day: LocalDate) = repository.observeDoses(account.id, day).first()

    @Test
    fun dailyMedicineProjectsOnePendingDosePerTimeInTimeOrder() = runTest {
        create("Magnésio", setOf(LocalTime.of(21, 30), LocalTime.of(8, 0)))

        val doses = doses(thursday.plusDays(3))

        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(21, 30)), doses.map { it.time })
        assertTrue(doses.all { it.name == "Magnésio" && it.status == DoseStatus.PENDING && it.takenAt == null })
        assertEquals(1, doses.first().amount)
        assertEquals(DoseUnit.CAPSULE, doses.first().unit)
    }

    @Test
    fun weekdayMedicineOnlyProjectsOnItsWeekdaysFromTheStartDate() = runTest {
        create(repeat = MedicineRepeat.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))

        assertEquals(1, doses(thursday).size)
        assertTrue(doses(thursday.plusDays(1)).isEmpty())
        assertEquals(1, doses(thursday.plusDays(4)).size)
        assertTrue("antes do início não projeta", doses(thursday.minusDays(7)).isEmpty())
    }

    @Test
    fun periodMedicineProjectsOnlyInsideItsStartAndEndDates() = runTest {
        val start = thursday.plusDays(2)
        create(repeat = MedicineRepeat.Period(start, start.plusDays(6)))

        assertTrue(doses(start.minusDays(1)).isEmpty())
        assertEquals(1, doses(start).size)
        assertEquals(1, doses(start.plusDays(6)).size)
        assertTrue(doses(start.plusDays(7)).isEmpty())
    }

    @Test
    fun takingADoseStampsTheMomentAndOnlyAffectsThatDay() = runTest {
        val medicine = create()
        val time = LocalTime.of(13, 0)

        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.TAKEN)

        val taken = doses(thursday).single()
        assertEquals(DoseStatus.TAKEN, taken.status)
        assertEquals(now, taken.takenAt)
        assertEquals(DoseStatus.PENDING, doses(thursday.plusDays(1)).single().status)
    }

    @Test
    fun aTakenDoseCanBeUnmarkedAndBecomesPendingWithoutATakenInstant() = runTest {
        val medicine = create()
        val time = LocalTime.of(13, 0)
        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.TAKEN)

        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.PENDING)

        val dose = doses(thursday).single()
        assertEquals(DoseStatus.PENDING, dose.status)
        assertNull(dose.takenAt)
    }

    @Test
    fun skippingADoseKeepsOtherTimesOfTheSameDayPending() = runTest {
        val medicine = create(times = setOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))

        repository.setDoseStatus(account.id, medicine.id, thursday.plusDays(2), LocalTime.of(8, 0), DoseStatus.SKIPPED)

        assertEquals(
            listOf(DoseStatus.SKIPPED, DoseStatus.PENDING),
            doses(thursday.plusDays(2)).map { it.status },
        )
        assertNull(doses(thursday.plusDays(2)).first().takenAt)
    }

    @Test
    fun observeMedicinesListsRegisteredMedicinesWithTheirTimesAndRepeat() = runTest {
        val created = create(
            "Magnésio",
            setOf(LocalTime.of(21, 30), LocalTime.of(8, 0)),
            MedicineRepeat.Weekdays(setOf(DayOfWeek.MONDAY)),
        )

        val listed = repository.observeMedicines(account.id).first()

        assertEquals(listOf(created), listed)
        assertEquals(setOf(LocalTime.of(8, 0), LocalTime.of(21, 30)), listed.single().times)
    }

    @Test
    fun takingADoseDeductsItsAmountFromTheStock() = runTest {
        val medicine = create(amount = 2, stock = 30, threshold = 5)

        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)

        assertEquals(28, stockOf(medicine))
    }

    @Test
    fun unmarkingATakenDoseGivesItsAmountBackToTheStock() = runTest {
        val medicine = create(amount = 2, stock = 30, threshold = 5)
        val time = LocalTime.of(13, 0)
        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.TAKEN)

        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.PENDING)

        assertEquals(30, stockOf(medicine))
    }

    @Test
    fun skippingNeverChangesTheStockAndTakingASkippedDoseDeductsOnce() = runTest {
        val medicine = create(stock = 10, threshold = 2)
        val time = LocalTime.of(13, 0)

        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.SKIPPED)
        assertEquals(10, stockOf(medicine))

        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.TAKEN)
        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.TAKEN)
        assertEquals(9, stockOf(medicine))

        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.SKIPPED)
        assertEquals(10, stockOf(medicine))
    }

    @Test
    fun theStockNeverGoesNegative() = runTest {
        val medicine = create(amount = 3, stock = 2, threshold = 1)

        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)

        assertEquals(0, stockOf(medicine))
    }

    @Test
    fun unmarkingAfterAClampedDeductionGivesBackOnlyWhatWasDeducted() = runTest {
        val medicine = create(amount = 3, stock = 2, threshold = 1)
        val time = LocalTime.of(13, 0)
        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.TAKEN)
        assertEquals(0, stockOf(medicine))

        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.PENDING)

        assertEquals(2, stockOf(medicine))
    }

    @Test
    fun partialDeductionIsRefundedExactlyEvenWhenOtherDosesWereTakenMeanwhile() = runTest {
        val medicine = create(amount = 2, stock = 3, threshold = 1)
        val time = LocalTime.of(13, 0)
        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.TAKEN)
        repository.setDoseStatus(account.id, medicine.id, thursday.plusDays(1), time, DoseStatus.TAKEN)
        assertEquals(0, stockOf(medicine))

        repository.setDoseStatus(account.id, medicine.id, thursday.plusDays(1), time, DoseStatus.PENDING)

        assertEquals(1, stockOf(medicine))
    }

    @Test
    fun medicineWithoutStockIsNotAffectedByDoses() = runTest {
        val medicine = create()

        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)

        val listed = repository.observeMedicines(account.id).first().single()
        assertNull(listed.stock)
    }

    @Test
    fun lowStockAlertsAtOrBelowTheThresholdOnly() = runTest {
        val medicine = create(stock = 7, threshold = 5)
        val time = LocalTime.of(13, 0)
        fun low() = repository.observeMedicines(account.id)

        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.TAKEN)
        assertEquals(false, low().first().single().stock?.low)
        repository.setDoseStatus(account.id, medicine.id, thursday.plusDays(1), time, DoseStatus.TAKEN)
        assertEquals(true, low().first().single().stock?.low)
        repository.setDoseStatus(account.id, medicine.id, thursday.plusDays(1), time, DoseStatus.PENDING)
        assertEquals(false, low().first().single().stock?.low)
    }
}
