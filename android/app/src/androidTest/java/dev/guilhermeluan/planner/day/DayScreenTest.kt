package dev.guilhermeluan.planner.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.guilhermeluan.planner.tasks.DayPlan
import dev.guilhermeluan.planner.tasks.PlannerTask
import dev.guilhermeluan.planner.tasks.TaskDraft
import dev.guilhermeluan.planner.tasks.TaskStatus
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun DaySeparatesPlannedItemsAndCreatesTaskFromItsPublicAction() {
        val day = LocalDate.of(2026, 7, 11)
        var created: TaskDraft? = null
        composeRule.setContent {
            PlannerTheme {
                DayScreen(
                    state = DayUiState(
                        selectedDay = day,
                        plan = DayPlan(
                            day,
                            tasks = listOf(
                                task("timed", "Enviar documentos", day, LocalTime.of(14, 0)),
                                task("untimed", "Organizar a semana", day, null),
                            ),
                            routines = emptyList(),
                        ),
                    ),
                    onSelectDay = {},
                    onCreateTask = { created = it },
                    onToggleTask = { _, _ -> },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("Rotinas").assertIsDisplayed()
        composeRule.onNodeWithText("Tarefas com horário").assertIsDisplayed()
        composeRule.onNodeWithText("Sem horário").assertIsDisplayed()
        composeRule.onNodeWithText("Enviar documentos").assertIsDisplayed()
        composeRule.onNodeWithText("Organizar a semana").assertIsDisplayed()

        composeRule.onNodeWithText("Nova Tarefa").performClick()
        composeRule.onNodeWithTag("task-title").performTextInput("Comprar café")
        composeRule.onNodeWithText("Adicionar ao Dia").performClick()

        composeRule.runOnIdle {
            assertEquals(TaskDraft("Comprar café", day, null), created)
        }
    }

    private fun task(
        id: String,
        title: String,
        day: LocalDate,
        time: LocalTime?,
    ) = PlannerTask(
        id = id,
        accountId = "account-1",
        plannerId = "planner-1",
        title = title,
        day = day,
        time = time,
        status = TaskStatus.PENDING,
        archived = false,
        version = 1,
    )
}
