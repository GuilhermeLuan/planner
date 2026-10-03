package dev.guilhermeluan.planner.water

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.testsupport.seed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WaterRepositoryTest {
    private lateinit var database: PlannerDatabase
    private lateinit var repository: WaterRepository
    private val account = Account("account-1", "ana", "America/Sao_Paulo", false)
    private val planner = Planner("planner-1", account.id)
    private val thursday = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.seed(account, planner)
        repository = WaterRepository(database, Clock.fixed(Instant.parse("2026-10-01T15:00:00Z"), ZoneOffset.UTC))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun addingWaterAccumulatesTheConsumptionOfTheDay() = runTest {
        repository.add(account.id, thursday, 200)
        repository.add(account.id, thursday, 500)

        assertEquals(700, repository.observeDay(account.id, thursday).first().consumedMl)
    }

    @Test
    fun adjustingReplacesTheTotalOfTheDay() = runTest {
        repository.add(account.id, thursday, 1200)

        repository.adjustTotal(account.id, thursday, 900)
        assertEquals(900, repository.observeDay(account.id, thursday).first().consumedMl)

        repository.adjustTotal(account.id, thursday.plusDays(1), 300)
        assertEquals(300, repository.observeDay(account.id, thursday.plusDays(1)).first().consumedMl)
    }

    @Test
    fun goalIsTwoLitersUntilThePersonDefinesOne() = runTest {
        assertEquals(2000, repository.observeDay(account.id, thursday).first().goalMl)
    }

    @Test
    fun changingTheGoalAppliesFromThatDayOnWithoutRewritingPastDays() = runTest {
        repository.setGoal(account.id, 2500, from = thursday)
        repository.setGoal(account.id, 3000, from = thursday.plusDays(3))

        assertEquals(2000, goalOn(thursday.minusDays(1)))
        assertEquals(2500, goalOn(thursday))
        assertEquals(2500, goalOn(thursday.plusDays(2)))
        assertEquals(3000, goalOn(thursday.plusDays(3)))
        assertEquals(3000, goalOn(thursday.plusDays(30)))
    }

    @Test
    fun changingTheGoalAgainFromAnEarlierDayReplacesLaterChanges() = runTest {
        repository.setGoal(account.id, 3000, from = thursday.plusDays(3))
        repository.setGoal(account.id, 1500, from = thursday)
        repository.setGoal(account.id, 1800, from = thursday)

        assertEquals(1800, goalOn(thursday))
        assertEquals(1800, goalOn(thursday.plusDays(3)))
    }

    @Test
    fun concurrentTapsAreAllCounted() = runBlocking {
        (1..40).map { async(Dispatchers.IO) { repository.add(account.id, thursday, 200) } }.awaitAll()

        assertEquals(8000, repository.observeDay(account.id, thursday).first().consumedMl)
    }

    @Test
    fun weekListsEveryDayWithItsConsumptionAndItsOwnGoal() = runTest {
        val monday = LocalDate.of(2026, 9, 28)
        repository.add(account.id, monday, 2000)
        repository.add(account.id, monday.plusDays(1), 1400)
        repository.setGoal(account.id, 1500, from = monday.plusDays(2))
        repository.add(account.id, monday.plusDays(2), 1500)
        repository.add(account.id, monday.plusDays(7), 900)

        val week = repository.observeWeek(account.id, monday..monday.plusDays(6)).first()

        assertEquals(
            listOf(
                WaterDay(monday, 2000, 2000),
                WaterDay(monday.plusDays(1), 1400, 2000),
                WaterDay(monday.plusDays(2), 1500, 1500),
                WaterDay(monday.plusDays(3), 0, 1500),
                WaterDay(monday.plusDays(4), 0, 1500),
                WaterDay(monday.plusDays(5), 0, 1500),
                WaterDay(monday.plusDays(6), 0, 1500),
            ),
            week,
        )
        assertEquals(listOf(true, false, true, false, false, false, false), week.map(WaterDay::goalMet))
    }

    private suspend fun goalOn(day: LocalDate) = repository.observeDay(account.id, day).first().goalMl
}
