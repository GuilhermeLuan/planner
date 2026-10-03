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

    private fun draftOf(
        medicine: PlannerMedicine,
        name: String = medicine.name,
        amount: Int = medicine.amount,
        times: Set<LocalTime> = medicine.times,
        repeat: MedicineRepeat = medicine.repeat,
        stock: Int? = medicine.stock?.amount,
        threshold: Int? = medicine.stock?.threshold,
    ) = MedicineDraft(name, amount, medicine.unit, times, repeat, medicine.startDate, stock, threshold)

    private suspend fun edit(medicine: PlannerMedicine, draft: MedicineDraft, from: LocalDate = thursday.plusDays(1)) =
        repository.editMedicine(account.id, medicine.id, draft, from)

    @Test
    fun editingChangesFutureDosesButKeepsRegisteredOnesAsTheyWere() = runTest {
        val medicine = create(times = setOf(LocalTime.of(13, 0)))
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)

        edit(medicine, draftOf(medicine, times = setOf(LocalTime.of(9, 0), LocalTime.of(20, 0))))

        val past = doses(thursday).single()
        assertEquals(LocalTime.of(13, 0) to DoseStatus.TAKEN, past.time to past.status)
        assertEquals(now, past.takenAt)
        assertEquals(listOf(LocalTime.of(9, 0), LocalTime.of(20, 0)), doses(thursday.plusDays(1)).map { it.time })
    }

    @Test
    fun editingOnADayWithARegisteredDoseOnlyTakesEffectFromTheNextDay() = runTest {
        val medicine = create(times = setOf(LocalTime.of(13, 0)))
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)

        edit(medicine, draftOf(medicine, name = "Vitamina D3", amount = 3, times = setOf(LocalTime.of(9, 0))), from = thursday)

        val today = doses(thursday).single()
        assertEquals(Triple("Vitamina D", 1, DoseStatus.TAKEN), Triple(today.name, today.amount, today.status))
        assertEquals(LocalTime.of(13, 0), today.time)
        val tomorrow = doses(thursday.plusDays(1)).single()
        assertEquals(Triple("Vitamina D3", 3, LocalTime.of(9, 0)), Triple(tomorrow.name, tomorrow.amount, tomorrow.time))
    }

    @Test
    fun editingOnADayWithNothingRegisteredAppliesToThatDayItself() = runTest {
        val medicine = create(times = setOf(LocalTime.of(13, 0)))

        edit(medicine, draftOf(medicine, times = setOf(LocalTime.of(9, 0))), from = thursday)

        assertEquals(listOf(LocalTime.of(9, 0)), doses(thursday).map { it.time })
    }

    @Test
    fun editingNeverRewritesPastDaysThatHadNoRegisteredDose() = runTest {
        val medicine = create(name = "Vitamina D", amount = 1)
        val sunday = thursday.plusDays(3)

        edit(
            medicine,
            draftOf(medicine, name = "Vitamina D3", amount = 2, times = setOf(LocalTime.of(9, 0)), repeat = MedicineRepeat.Weekdays(setOf(DayOfWeek.MONDAY))),
            from = thursday.plusDays(7),
        )

        val before = doses(sunday).single()
        assertEquals(Triple("Vitamina D", 1, LocalTime.of(13, 0)), Triple(before.name, before.amount, before.time))
        assertEquals(1, doses(thursday.plusDays(6)).size)
        assertTrue("quinta de depois da edição não repete mais", doses(thursday.plusDays(7)).isEmpty())
        val monday = doses(thursday.plusDays(11)).single()
        assertEquals(Triple("Vitamina D3", 2, LocalTime.of(9, 0)), Triple(monday.name, monday.amount, monday.time))
    }

    @Test
    fun movingAPeriodStartEarlierDoesNotCreateDosesInPastDays() = runTest {
        val start = thursday.plusDays(10)
        val medicine = create(repeat = MedicineRepeat.Period(start, start.plusDays(5)))

        edit(medicine, draftOf(medicine, repeat = MedicineRepeat.Period(thursday, start.plusDays(5))), from = thursday.plusDays(2))

        assertTrue(doses(thursday).isEmpty())
        assertEquals(1, doses(thursday.plusDays(2)).size)
    }

    @Test
    fun switchingFromAPeriodToEveryDayStartsFromTheEditDay() = runTest {
        val medicine = create(repeat = MedicineRepeat.Period(thursday, thursday.plusDays(3)))

        edit(medicine, draftOf(medicine, repeat = MedicineRepeat.Daily), from = thursday.plusDays(5))

        assertTrue(doses(thursday.plusDays(4)).isEmpty())
        assertEquals(1, doses(thursday.plusDays(5)).size)
    }

    @Test
    fun editingTwiceOnTheSameDayStillKeepsTheOriginalForThePast() = runTest {
        val medicine = create()
        edit(medicine, draftOf(medicine, times = setOf(LocalTime.of(9, 0))))
        val once = repository.observeMedicines(account.id).first().single()

        edit(once, draftOf(once, times = setOf(LocalTime.of(10, 0))))

        assertEquals(LocalTime.of(13, 0), doses(thursday).single().time)
        assertEquals(LocalTime.of(10, 0), doses(thursday.plusDays(1)).single().time)
    }

    @Test
    fun editRejectsAnInvalidRepeat() = runTest {
        val medicine = create()

        val badPeriod = runCatching { edit(medicine, draftOf(medicine, repeat = MedicineRepeat.Period(thursday.plusDays(3), thursday))) }
        val noWeekdays = runCatching { edit(medicine, draftOf(medicine, repeat = MedicineRepeat.Weekdays(emptySet()))) }
        val badCreate = runCatching {
            repository.createMedicine(account.id, planner.id, draftOf(medicine, repeat = MedicineRepeat.Weekdays(emptySet())))
        }

        assertTrue(badPeriod.exceptionOrNull() is IllegalArgumentException)
        assertTrue(noWeekdays.exceptionOrNull() is IllegalArgumentException)
        assertTrue(badCreate.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun archivingStopsDosesFromThatDayAndRestoringDoesNotBringBackTheGap() = runTest {
        val medicine = create(stock = 10, times = setOf(LocalTime.of(8, 0), LocalTime.of(13, 0)))
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(8, 0), DoseStatus.TAKEN)

        repository.archiveMedicine(account.id, medicine.id, thursday.plusDays(2))

        assertTrue(repository.observeMedicines(account.id).first().isEmpty())
        assertEquals(listOf(medicine.id), repository.observeArchivedMedicines(account.id).first().map { it.id })
        assertEquals("o histórico anterior continua", 2, doses(thursday.plusDays(1)).size)
        assertTrue(doses(thursday.plusDays(2)).isEmpty())
        assertTrue(doses(thursday.plusDays(3)).isEmpty())

        repository.restoreMedicine(account.id, medicine.id, thursday.plusDays(5))

        assertTrue(repository.observeArchivedMedicines(account.id).first().isEmpty())
        assertEquals(listOf(DoseStatus.TAKEN, DoseStatus.PENDING), doses(thursday).map { it.status })
        assertEquals(9, stockOf(medicine))
        assertTrue("o período arquivado não volta como pendente", doses(thursday.plusDays(4)).isEmpty())
        assertEquals(2, doses(thursday.plusDays(5)).size)
    }

    @Test
    fun aDoseRegisteredBeforeArchivingStaysVisibleWhileArchived() = runTest {
        val medicine = create()
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)

        repository.archiveMedicine(account.id, medicine.id, thursday)

        assertEquals(listOf(DoseStatus.TAKEN), doses(thursday).map { it.status })
        assertTrue(doses(thursday.plusDays(1)).isEmpty())
    }

    @Test
    fun archivingAndRestoringOnTheSameDayLeavesNoGap() = runTest {
        val medicine = create()

        repository.archiveMedicine(account.id, medicine.id, thursday.plusDays(1))
        repository.restoreMedicine(account.id, medicine.id, thursday.plusDays(1))
        repository.archiveMedicine(account.id, medicine.id, thursday.plusDays(1))
        repository.restoreMedicine(account.id, medicine.id, thursday.plusDays(1))

        assertEquals(1, doses(thursday.plusDays(1)).size)
    }

    @Test
    fun aDoseTakenWhileTheEditFormIsOpenIsNotUndoneBySavingTheUntouchedStock() = runTest {
        val medicine = create(stock = 10, threshold = 3)
        // O formulário abre mostrando 10; enquanto isso, "Tomei" na notificação desconta 1.
        val draft = draftOf(medicine, name = "Vitamina D3").copy(stockAsShown = 10)
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)

        edit(medicine, draft)

        val edited = repository.observeMedicines(account.id).first().single()
        assertEquals("Vitamina D3", edited.name)
        assertEquals(9, edited.stock?.amount)
        assertEquals(10, edited.stock?.capacity)
    }

    @Test
    fun editingStockReplacesItAndUndoingAnEarlierTakenDoseStillReturnsWhatItDeducted() = runTest {
        val medicine = create(amount = 2, stock = 10, threshold = 3)
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)

        edit(medicine, draftOf(medicine, amount = 1, stock = 30, threshold = 5))

        val edited = repository.observeMedicines(account.id).first().single()
        assertEquals(30, edited.stock?.amount)
        assertEquals(5, edited.stock?.threshold)
        assertEquals(1, edited.amount)
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.PENDING)
        assertEquals(32, stockOf(medicine))

        edit(medicine, draftOf(medicine, stock = null))
        assertNull(stockOf(medicine))
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.PENDING)
        assertNull("sem Estoque nada é descontado nem devolvido", stockOf(medicine))
    }

    @Test
    fun stockWithoutAThresholdWarnsOnlyWhenItRunsOut() = runTest {
        val medicine = create(stock = 2, threshold = null)

        assertEquals(false, repository.observeMedicines(account.id).first().single().stock?.low)
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)
        repository.setDoseStatus(account.id, medicine.id, thursday.plusDays(1), LocalTime.of(13, 0), DoseStatus.TAKEN)
        assertEquals(true, repository.observeMedicines(account.id).first().single().stock?.low)
    }

    @Test
    fun takingADoseOfAnEarlierDayDeductsTheAmountThatDayShowsNotTheEditedOne() = runTest {
        val medicine = create(amount = 1, stock = 10)

        edit(medicine, draftOf(medicine, amount = 2))

        assertEquals(1, doses(thursday).single().amount)
        repository.setDoseStatus(account.id, medicine.id, thursday, LocalTime.of(13, 0), DoseStatus.TAKEN)
        assertEquals(9, stockOf(medicine))
        repository.setDoseStatus(account.id, medicine.id, thursday.plusDays(1), LocalTime.of(13, 0), DoseStatus.TAKEN)
        assertEquals(7, stockOf(medicine))
    }

    @Test
    fun lastRegisteredDaysFollowTheLatestDoseThatLeftPendingForEachMedicine() = runTest {
        val vitamin = create()
        val magnesium = repository.createMedicine(account.id, planner.id, draftOf(vitamin, name = "Magnésio"))
        val time = LocalTime.of(13, 0)
        val lastDays = { repository.observeLastRegisteredDays(account.id) }

        repository.setDoseStatus(account.id, vitamin.id, thursday, time, DoseStatus.TAKEN)
        repository.setDoseStatus(account.id, vitamin.id, thursday.plusDays(1), time, DoseStatus.SKIPPED)
        assertEquals(mapOf(vitamin.id to thursday.plusDays(1)), lastDays().first())

        repository.setDoseStatus(account.id, vitamin.id, thursday.plusDays(1), time, DoseStatus.PENDING)
        assertEquals(mapOf(vitamin.id to thursday), lastDays().first())
        assertNull(lastDays().first()[magnesium.id])
    }

    @Test
    fun anEditThatTakesEffectSoonerThanAnEarlierOneShowsUpFromItsOwnDay() = runTest {
        val medicine = create()
        val saturday = thursday.plusDays(2)
        val time = LocalTime.of(13, 0)
        repository.setDoseStatus(account.id, medicine.id, saturday, time, DoseStatus.SKIPPED)
        edit(medicine, draftOf(medicine, name = "Vitamina D3"), from = thursday)
        repository.setDoseStatus(account.id, medicine.id, saturday, time, DoseStatus.PENDING)

        edit(medicine, draftOf(medicine, name = "Vitamina D2000"), from = thursday)

        assertEquals(
            List(5) { "Vitamina D2000" },
            (0L..4L).map { doses(thursday.plusDays(it)).single().name },
        )
    }

    @Test
    fun archivingHidesDosesAlreadyRegisteredOnLaterDaysAndRestoringBringsThemBack() = runTest {
        val medicine = create()
        val time = LocalTime.of(13, 0)
        val saturday = thursday.plusDays(2)
        repository.setDoseStatus(account.id, medicine.id, thursday, time, DoseStatus.TAKEN)
        repository.setDoseStatus(account.id, medicine.id, saturday, time, DoseStatus.SKIPPED)

        repository.archiveMedicine(account.id, medicine.id, thursday)

        assertEquals("a Dose de hoje segue visível", listOf(DoseStatus.TAKEN), doses(thursday).map { it.status })
        assertTrue(doses(saturday).isEmpty())

        repository.restoreMedicine(account.id, medicine.id, thursday.plusDays(1))

        assertEquals(listOf(DoseStatus.SKIPPED), doses(saturday).map { it.status })
    }
}
