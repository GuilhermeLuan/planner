package dev.guilhermeluan.planner.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import dev.guilhermeluan.planner.tasks.ArchivedItems
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import dev.guilhermeluan.planner.you.SettingsScreen
import dev.guilhermeluan.planner.you.YouUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AccountSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun invalidTimezoneIsVisibleAndValidTimezoneCanBeSaved() {
        var saved: String? = null
        composeRule.setContent {
            PlannerTheme {
                SettingsScreen(
                    state = YouUiState(
                        name = "Gui",
                        timezone = "America/Sao_Paulo",
                        memberSince = null,
                        today = LocalDate.of(2026, 10, 19),
                        consistency = null,
                        notificationsEnabled = true,
                        lastBackup = null,
                        archived = ArchivedItems(),
                    ),
                    onBack = {},
                    onSaveName = {},
                    onSaveTimezone = { saved = it },
                    onOpenNotificationSettings = {},
                    onExportBackup = {},
                    onImportBackup = {},
                    onRestoreTask = {},
                    onRestoreRoutine = {},
                    onRestoreMedicine = {},
                    detectedTimezone = "America/Sao_Paulo",
                )
            }
        }

        composeRule.onNodeWithText("Alterar").performClick()
        composeRule.onNodeWithTag("account-timezone").performTextClearance()
        composeRule.onNodeWithTag("account-timezone").performTextInput("Mars/Olympus_Mons")
        composeRule.onNodeWithText("Fuso da Conta inválido").assertIsDisplayed()

        composeRule.onNodeWithTag("account-timezone").performTextClearance()
        composeRule.onNodeWithTag("account-timezone").performTextInput("Europe/Lisbon")
        composeRule.onNodeWithText("Salvar fuso").performClick()

        composeRule.runOnIdle { assertEquals("Europe/Lisbon", saved) }
    }
}
