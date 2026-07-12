package dev.guilhermeluan.planner.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PasswordChangeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun matchingPrivatePasswordCanCompleteTheMandatoryChange() {
        var submitted: String? = null
        composeRule.setContent {
            PlannerTheme {
                PasswordChangeScreen(
                    username = "gui",
                    state = PasswordChangeUiState(),
                    onChangePassword = { submitted = it },
                )
            }
        }

        composeRule.onNodeWithTag("new-password").performTextInput("new-private-password")
        composeRule.onNodeWithTag("confirm-password").performTextInput("new-private-password")
        composeRule.onNodeWithText("Salvar nova senha").performClick()

        composeRule.runOnIdle { assertEquals("new-private-password", submitted) }
    }
}
