package dev.guilhermeluan.planner.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun invalidTimezoneIsVisibleAndValidTimezoneCanBeSaved() {
        var saved: String? = null
        composeRule.setContent {
            PlannerTheme {
                AccountSettingsScreen(
                    currentName = "Gui",
                    currentTimezone = "America/Sao_Paulo",
                    detectedTimezone = "America/Sao_Paulo",
                    onSaveName = {},
                    onSaveTimezone = { saved = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("account-timezone").performTextClearance()
        composeRule.onNodeWithTag("account-timezone").performTextInput("Mars/Olympus_Mons")
        composeRule.onNodeWithText("Fuso da Conta inválido").assertIsDisplayed()

        composeRule.onNodeWithTag("account-timezone").performTextClearance()
        composeRule.onNodeWithTag("account-timezone").performTextInput("Europe/Lisbon")
        composeRule.onNodeWithText("Salvar fuso").performClick()

        composeRule.runOnIdle { assertEquals("Europe/Lisbon", saved) }
    }
}
