package dev.guilhermeluan.planner.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import dev.guilhermeluan.planner.tasks.DayPlan
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayScreenHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerShowsSceneWithGreetingAndWeekdayDate() {
        val day = LocalDate.of(2026, 10, 1)
        composeRule.setContent {
            PlannerTheme {
                DayScreen(
                    state = DayUiState(selectedDay = day, plan = DayPlan(day, tasks = emptyList(), routines = emptyList())),
                    onSelectDay = {},
                    onCreateTask = {},
                    onToggleTask = { _, _ -> },
                    userName = "Guilherme",
                    now = LocalTime.of(21, 0),
                )
            }
        }

        composeRule.onNodeWithTag("planner-scene").assertIsDisplayed()
        composeRule.onNodeWithText("Boa noite, Guilherme").assertIsDisplayed()
        composeRule.onNodeWithText("Quinta, 1 de outubro").assertIsDisplayed()
        composeRule.onNodeWithText("Ver mês").assertIsDisplayed()
    }
}
