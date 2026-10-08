package dev.guilhermeluan.planner.water

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class WaterScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 1)
    private val monday = LocalDate.of(2026, 9, 28)

    private fun show(
        consumedMl: Int = 1200,
        goalMl: Int = 2000,
        week: List<WaterDay> = emptyList(),
        onAdd: (Int) -> Unit = {},
        onAdjustTotal: (Int) -> Unit = {},
        onSetGoal: (Int) -> Unit = {},
    ) = composeRule.setContent {
        PlannerTheme(darkTheme = false) {
            WaterScreen(
                state = WaterUiState(WaterDay(today, consumedMl, goalMl), week),
                onAdd = onAdd,
                onAdjustTotal = onAdjustTotal,
                onSetGoal = onSetGoal,
                onSaveReminder = {},
            )
        }
    }

    @Test
    fun reminderCardSaysWhetherTheWaterRemindersAreOnAndHowOften() {
        var reminder by mutableStateOf(WaterReminderSettings(enabled = true, intervalHours = 2, windowStart = LocalTime.of(8, 0), windowEnd = LocalTime.of(20, 0)))
        composeRule.setContent {
            PlannerTheme(darkTheme = false) {
                WaterScreen(
                    state = WaterUiState(WaterDay(today, 1200, 2000), reminder = reminder),
                    onAdd = {},
                    onAdjustTotal = {},
                    onSetGoal = {},
                    onSaveReminder = {},
                )
            }
        }

        composeRule.onNodeWithTag("water-reminder-card").assertIsDisplayed()
        composeRule.onNodeWithText("A cada 2 h · 08:00–20:00").assertIsDisplayed()
        composeRule.runOnIdle { reminder = WaterReminderSettings() }
        composeRule.onNodeWithText("Desligado").assertIsDisplayed()
    }

    @Test
    fun cupShowsTheConsumptionAgainstTheGoalAndWhatIsLeft() {
        show(consumedMl = 1200, goalMl = 2000)

        composeRule.onNodeWithText("Água").assertIsDisplayed()
        composeRule.onNodeWithText("Meta de 2 litros por dia").assertIsDisplayed()
        composeRule.onNodeWithTag("water-cup").assertIsDisplayed()
        composeRule.onNodeWithText("1.200").assertIsDisplayed()
        composeRule.onNodeWithText("de 2.000 ml").assertIsDisplayed()
        composeRule.onNodeWithText("Faltam 800 ml — cerca de quatro copos.").assertIsDisplayed()
    }

    @Test
    fun quickButtonsAddAGlassOrABottle() {
        val added = mutableListOf<Int>()
        show(onAdd = { added += it })

        composeRule.onNodeWithText("+ 200 ml").performClick()
        composeRule.onNodeWithText("+ 500 ml").performClick()

        assertEquals(listOf(200, 500), added)
        composeRule.onNodeWithText("Copo").assertIsDisplayed()
        composeRule.onNodeWithText("Garrafa").assertIsDisplayed()
    }

    @Test
    fun otherValueAddsAnyAmount() {
        val added = mutableListOf<Int>()
        show(onAdd = { added += it })

        composeRule.onNodeWithText("Outro valor").performClick()
        composeRule.onNodeWithTag("water-amount").performTextInput("350")
        composeRule.onNodeWithText("Somar água").performClick()

        assertEquals(listOf(350), added)
    }

    @Test
    fun otherValueAdjustsTheTotalOfTheDayStartingFromTheCurrentTotal() {
        val adjusted = mutableListOf<Int>()
        show(consumedMl = 1200, onAdjustTotal = { adjusted += it })

        composeRule.onNodeWithText("ajustar total").performClick()
        composeRule.onNodeWithText("Ajustar total").performClick()
        composeRule.onNodeWithTag("water-amount").assertTextContains("1200")
        composeRule.onNodeWithTag("water-amount").performTextReplacement("900")
        composeRule.onNodeWithText("Salvar total").performClick()

        assertEquals(listOf(900), adjusted)
    }

    @Test
    fun otherValueCannotAddNothing() {
        show()

        composeRule.onNodeWithText("Outro valor").performClick()

        composeRule.onNodeWithText("Somar água").assertIsNotEnabled()
    }

    @Test
    fun weekChartShowsEachDayAndHowManyDaysMetTheGoal() {
        val totals = listOf(2000, 1400, 2100, 1200, 2000, 0, 0)
        show(week = totals.mapIndexed { i, ml -> WaterDay(monday.plusDays(i.toLong()), ml, 2000) })

        composeRule.onNodeWithText("Esta semana").assertIsDisplayed()
        composeRule.onNodeWithText("Meta batida em 3 dias").assertIsDisplayed()
        composeRule.onNodeWithTag("water-bar-2026-09-29")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.7f, 0f..1f)))
        composeRule.onNodeWithTag("water-bar-2026-09-30")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(1f, 0f..1f)))
        composeRule.onNodeWithTag("water-bar-2026-10-01").assertContentDescriptionEquals("quinta-feira: 1.200 de 2.000 ml")
    }

    @Test
    fun weekChartSaysWhenNoDayMetTheGoalYetAndCountsASingleDay() {
        show(week = (0..6).map { WaterDay(monday.plusDays(it.toLong()), if (it == 0) 2500 else 0, 2000) })

        composeRule.onNodeWithText("Meta batida em 1 dia").assertIsDisplayed()
    }

    @Test
    fun personDefinesTheWaterGoalFromTheHeader() {
        val goals = mutableListOf<Int>()
        show(goalMl = 2000, onSetGoal = { goals += it })

        composeRule.onNodeWithText("Meta de 2 litros por dia").performClick()
        composeRule.onNodeWithTag("water-goal").assertTextContains("2000")
        composeRule.onNodeWithText("2,5 L").performClick()
        composeRule.onNodeWithText("Salvar meta").performClick()

        assertEquals(listOf(2500), goals)
    }

    @Test
    fun goalSheetAcceptsAnyAmountTyped() {
        val goals = mutableListOf<Int>()
        show(onSetGoal = { goals += it })

        composeRule.onNodeWithText("Meta de 2 litros por dia").performClick()
        composeRule.onNodeWithTag("water-goal").performTextReplacement("1750")
        composeRule.onNodeWithText("Salvar meta").performClick()

        assertEquals(listOf(1750), goals)
    }

    @Test
    fun reachingTheGoalCelebratesInsteadOfCountingWhatIsLeft() {
        show(consumedMl = 2300, goalMl = 2000)

        composeRule.onNodeWithText("2.300").assertIsDisplayed()
        composeRule.onNodeWithText("Você bateu a meta do dia.").assertIsDisplayed()
    }
}
