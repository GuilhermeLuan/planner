package dev.guilhermeluan.planner.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.guilhermeluan.planner.tasks.RoutineDraft
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1000dp")
class RoutineFormTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val thursday = LocalDate.of(2026, 10, 1)

    private fun show(onSave: (RoutineDraft) -> Unit = {}) = composeRule.setContent {
        PlannerTheme { RoutineForm(initialDay = thursday, onSave = onSave) }
    }

    @Test
    fun saveStaysDisabledUntilTheRoutineHasANameAndWeekdays() {
        show()

        composeRule.onNodeWithText("Salvar rotina").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun weekdayChipsStartOnTheSelectedDayAndCanBeToggled() {
        var saved: RoutineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("routine-title").performTextInput("Alongar")
        composeRule.onNodeWithText("Seg").performScrollTo().performClick()
        composeRule.onNodeWithText("Sex").performScrollTo().performClick()
        composeRule.onNodeWithText("Salvar rotina").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                RoutineDraft("Alongar", setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY, DayOfWeek.FRIDAY), thursday, null),
                saved,
            )
        }
    }

    @Test
    fun todoDiaChipSelectsEverySevenWeekdays() {
        var saved: RoutineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("routine-title").performTextInput("Beber chá")
        composeRule.onNodeWithText("Todo dia").performScrollTo().performClick()
        composeRule.onNodeWithText("Salvar rotina").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(DayOfWeek.entries.toSet(), saved?.weekdays) }
    }

    @Test
    fun startDateShowsAReadableDateAndOpensACalendar() {
        show()

        composeRule.onNodeWithText("1 de outubro de 2026").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("routine-start-date").performScrollTo().performClick()
        composeRule.onNodeWithText("Confirmar").assertIsDisplayed()
    }
}
