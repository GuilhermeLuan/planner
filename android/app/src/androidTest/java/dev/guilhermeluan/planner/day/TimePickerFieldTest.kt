package dev.guilhermeluan.planner.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class TimePickerFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clockPickerOpensFromOptionalTimeField() {
        composeRule.setContent {
            PlannerTheme {
                OptionalTimePickerField(
                    value = LocalTime.of(9, 30),
                    onValueChange = {},
                    label = "Horário opcional",
                    tag = "time-field",
                )
            }
        }

        composeRule.onNodeWithTag("time-field").performClick()
        composeRule.onNodeWithText("Escolher horário").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelar").performClick()
    }
}
