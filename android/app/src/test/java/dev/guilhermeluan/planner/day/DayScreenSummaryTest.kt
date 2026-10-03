package dev.guilhermeluan.planner.day

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.guilhermeluan.planner.tasks.DayPlan
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
class DayScreenSummaryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val thursday = LocalDate.of(2026, 10, 1)
    private val summary = DaySummary(
        routines = SummaryItem("2 de 4", 0.5f),
        water = SummaryItem("1,2 de 2 L", 0.6f),
        medicines = SummaryItem("1 de 3", 1f / 3f),
    )

    private fun show(
        summary: DaySummary = this.summary,
        onOpenWater: () -> Unit = {},
        onOpenMedicines: () -> Unit = {},
    ) = composeRule.setContent {
        PlannerTheme {
            DayScreen(
                state = DayUiState(thursday, DayPlan(thursday, emptyList(), emptyList())),
                onSelectDay = {},
                onCreateTask = {},
                onToggleTask = { _, _ -> },
                summary = summary,
                onOpenWater = onOpenWater,
                onOpenMedicines = onOpenMedicines,
                userName = "Gui",
                now = LocalTime.of(9, 0),
            )
        }
    }

    private fun card(tag: String, text: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun showsTheThreeSummariesOfTheDay() {
        show()

        composeRule.onNodeWithText("Seu dia").performScrollTo().assertIsDisplayed()
        card("summary-routines", "2 de 4")
        card("summary-water", "1,2 de 2 L")
        card("summary-medicines", "1 de 3")
        composeRule.onNode(hasText("Rotinas") and hasAnyAncestor(hasTestTag("summary-routines")), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun tappingWaterOrMedicinesOpensTheirTab() {
        var opened = emptyList<String>()
        show(onOpenWater = { opened = opened + "water" }, onOpenMedicines = { opened = opened + "medicines" })

        composeRule.onNodeWithTag("summary-water").performScrollTo().performClick()
        composeRule.onNodeWithTag("summary-medicines").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(listOf("water", "medicines"), opened) }
    }

    @Test
    fun tappingRoutinesStaysOnToday() {
        var opened = 0
        show(onOpenWater = { opened++ }, onOpenMedicines = { opened++ })

        composeRule.onNodeWithTag("summary-routines").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(0, opened) }
    }

    @Test
    fun clickableTilesSayWhereTheyGo() {
        show()

        composeRule.onNode(
            hasTestTag("summary-water") and SemanticsMatcher("opens the Água tab") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Abrir a aba Água"
            },
        ).assertExists()
        composeRule.onNode(
            hasTestTag("summary-medicines") and SemanticsMatcher("opens the Remédios tab") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Abrir a aba Remédios"
            },
        ).assertExists()
    }

    @Test
    fun tilesFollowTheSummaryWhenTheSelectedDayChanges() {
        var current by mutableStateOf(summary)
        composeRule.setContent {
            PlannerTheme {
                DayScreen(
                    state = DayUiState(thursday, DayPlan(thursday, emptyList(), emptyList())),
                    onSelectDay = {},
                    onCreateTask = {},
                    onToggleTask = { _, _ -> },
                    summary = current,
                )
            }
        }
        card("summary-routines", "2 de 4")

        composeRule.runOnIdle {
            current = DaySummary(SummaryItem("0 de 1", 0f), SummaryItem("—", 0f), SummaryItem("3 de 3", 1f))
        }

        card("summary-routines", "0 de 1")
        card("summary-water", "—")
        card("summary-medicines", "3 de 3")
    }
}
