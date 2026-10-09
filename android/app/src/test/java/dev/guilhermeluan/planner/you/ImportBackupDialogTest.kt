package dev.guilhermeluan.planner.you

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class ImportBackupDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        onConfirm: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) = composeRule.setContent {
        PlannerTheme(darkTheme = false) {
            ImportBackupDialog(onConfirm = onConfirm, onDismiss = onDismiss)
        }
    }

    @Test
    fun dialogShowsTheTitleAndWarnsThatThePlannerWillBeReplaced() {
        show()

        composeRule.onNodeWithText("Importar backup?").assertIsDisplayed()
        composeRule.onNodeWithText("O Planner atual será apagado e substituído pelo do backup. Isso não pode ser desfeito.").assertIsDisplayed()
    }

    @Test
    fun replaceConfirmsTheImportAndDoesNotDismiss() {
        var confirmed = 0
        var dismissed = 0
        show(onConfirm = { confirmed++ }, onDismiss = { dismissed++ })

        composeRule.onNodeWithText("Substituir").performClick()

        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun cancelDismissesAndDoesNotConfirm() {
        var confirmed = 0
        var dismissed = 0
        show(onConfirm = { confirmed++ }, onDismiss = { dismissed++ })

        composeRule.onNodeWithText("Cancelar").performClick()

        assertEquals(0, confirmed)
        assertEquals(1, dismissed)
    }
}
