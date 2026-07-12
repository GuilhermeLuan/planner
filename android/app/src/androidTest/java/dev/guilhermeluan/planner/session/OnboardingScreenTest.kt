package dev.guilhermeluan.planner.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstOpeningRequestsOnlyTheNameAndSuggestsTheDeviceTimezone() {
        var submittedName: String? = null
        composeRule.setContent {
            PlannerTheme {
                OnboardingScreen(
                    timezone = "America/Sao_Paulo",
                    isSaving = false,
                    error = null,
                    onCreatePlanner = { submittedName = it },
                )
            }
        }

        composeRule.onNodeWithText("Seu nome").assertIsDisplayed()
        composeRule.onNodeWithText("Fuso sugerido: America/Sao_Paulo").assertIsDisplayed()
        composeRule.onNodeWithText("URL do servidor").assertDoesNotExist()
        composeRule.onNodeWithText("Usuário").assertDoesNotExist()
        composeRule.onNodeWithText("Senha").assertDoesNotExist()

        composeRule.onNodeWithTag("person-name").performTextInput("Guilherme")
        composeRule.onNodeWithText("Criar meu Planner").performClick()
        composeRule.runOnIdle { assertEquals("Guilherme", submittedName) }
    }
}
