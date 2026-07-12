package dev.guilhermeluan.planner.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SetupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun httpRequiresVisibleConfirmationBeforeConfigurationContinues() {
        var configured: ServerConfiguration? = null
        composeRule.setContent {
            PlannerTheme {
                SetupScreen(onConfigured = { configured = it })
            }
        }

        composeRule.onNodeWithTag("server-url").performTextInput("http://192.168.1.20:8080")
        composeRule.onNodeWithText("Continuar").performClick()

        composeRule.onNodeWithText("Sua conexão não estará protegida").assertIsDisplayed()
        composeRule.onNodeWithText("Credenciais e dados podem ser lidos durante o trajeto em HTTP.")
            .assertIsDisplayed()
        assertNull(configured)

        composeRule.onNodeWithText("Entendi, usar HTTP").performClick()
        composeRule.runOnIdle {
            check(configured?.baseUrl == "http://192.168.1.20:8080")
        }
    }
}
