package dev.guilhermeluan.planner.day

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.guilhermeluan.planner.tasks.DayPlan
import dev.guilhermeluan.planner.tasks.PlannedRoutineOccurrence
import dev.guilhermeluan.planner.tasks.PlannerTask
import dev.guilhermeluan.planner.tasks.RoutineOccurrenceStatus
import dev.guilhermeluan.planner.tasks.TaskDraft
import dev.guilhermeluan.planner.tasks.TaskStatus
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
@Config(sdk = [34], qualifiers = "w400dp-h1000dp")
class DayScreenTodayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val thursday = LocalDate.of(2026, 10, 1)

    private fun show(
        plan: DayPlan = DayPlan(thursday, emptyList(), emptyList()),
        markedDays: Set<LocalDate> = emptySet(),
        onSelectDay: (LocalDate) -> Unit = {},
        onToggleRoutine: (String, LocalDate, RoutineOccurrenceStatus) -> Unit = { _, _, _ -> },
        onCreateTask: (TaskDraft) -> Unit = {},
        onArchiveTask: (String) -> Unit = {},
    ) = composeRule.setContent {
        PlannerTheme {
            DayScreen(
                state = DayUiState(thursday, plan, markedDays = markedDays),
                onSelectDay = onSelectDay,
                onCreateTask = onCreateTask,
                onToggleTask = { _, _ -> },
                onToggleRoutine = onToggleRoutine,
                onArchiveTask = onArchiveTask,
                userName = "Gui",
                now = LocalTime.of(9, 0),
            )
        }
    }

    @Test
    fun weekStripShowsMondayToSundayMarksItemDaysAndSelectsADay() {
        var selected: LocalDate? = null
        show(
            markedDays = setOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 3)),
            onSelectDay = { selected = it },
        )

        composeRule.onAllNodes(
            SemanticsMatcher("is a week day") { node ->
                SemanticsProperties.TestTag in node.config &&
                    node.config[SemanticsProperties.TestTag].startsWith("week-day-")
            },
            useUnmergedTree = true,
        ).assertCountEquals(7)
        composeRule.onAllNodesWithTag("week-marker", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithTag("week-day-2026-10-04").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(LocalDate.of(2026, 10, 4), selected) }
    }

    @Test
    fun routineListCompletesAnOccurrenceFromItsCircleAndOffersNovaRotina() {
        var toggled: Triple<String, LocalDate, RoutineOccurrenceStatus>? = null
        show(
            plan = DayPlan(
                thursday,
                tasks = emptyList(),
                routines = listOf(PlannedRoutineOccurrence("r1:$thursday", "Meditar", thursday, LocalTime.of(7, 0))),
            ),
            onToggleRoutine = { id, day, status -> toggled = Triple(id, day, status) },
        )

        composeRule.onNodeWithText("Meditar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Nova rotina").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Concluir Meditar").performClick()

        composeRule.runOnIdle {
            assertEquals(Triple("r1", thursday, RoutineOccurrenceStatus.DONE), toggled)
        }
    }

    @Test
    fun taskTimelineListsTimedTasksBeforeLivreAndCountsRemaining() {
        fun task(id: String, title: String, time: LocalTime?, status: TaskStatus = TaskStatus.PENDING) =
            PlannerTask(id, "a", "p", title, thursday, time, status, archived = false, version = 1)
        show(
            plan = DayPlan(
                thursday,
                tasks = listOf(
                    task("t1", "Enviar documentos", LocalTime.of(14, 0)),
                    task("t2", "Ligar para o banco", LocalTime.of(16, 30), TaskStatus.DONE),
                    task("t3", "Organizar a semana", null),
                ),
                routines = emptyList(),
            ),
        )

        composeRule.onNodeWithText("2 restantes").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("14:00").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Livre").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Organizar a semana").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun floatingNovaTarefaButtonIsAlwaysOffered() {
        show()

        composeRule.onNodeWithTag("new-task-fab").assertIsDisplayed()
        composeRule.onNodeWithText("Nova tarefa", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun greetingUsesTheAccountNameAndVerMesOpensTheMonthlyCalendar() {
        show()

        composeRule.onNodeWithText("Bom dia, Gui").assertIsDisplayed()
        composeRule.onNodeWithText("Ver mês", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Calendário mensal").assertIsDisplayed()
    }

    @Test
    fun taskMenuArchivesATask() {
        var archived: String? = null
        show(
            plan = DayPlan(
                thursday,
                tasks = listOf(PlannerTask("t1", "a", "p", "Pagar conta", thursday, null, TaskStatus.PENDING, false, 1)),
                routines = emptyList(),
            ),
            onArchiveTask = { archived = it },
        )

        composeRule.onNodeWithContentDescription("Ações de Pagar conta").performScrollTo().performClick()
        composeRule.onNodeWithText("Arquivar").performClick()

        composeRule.runOnIdle { assertEquals("t1", archived) }
    }
}
