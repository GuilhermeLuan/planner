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

class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun credentialsAreSubmittedFromTheVisibleForm() {
        var submitted: Pair<String, String>? = null
        composeRule.setContent {
            PlannerTheme {
                LoginScreen(
                    serverName = "planner.local",
                    state = LoginUiState(),
                    onLogin = { username, password -> submitted = username to password },
                    onChangeServer = {},
                )
            }
        }

        composeRule.onNodeWithTag("username").performTextInput("gui")
        composeRule.onNodeWithTag("password").performTextInput("correct-password")
        composeRule.onNodeWithText("Entrar").performClick()

        composeRule.runOnIdle {
            assertEquals("gui" to "correct-password", submitted)
        }
    }
}
