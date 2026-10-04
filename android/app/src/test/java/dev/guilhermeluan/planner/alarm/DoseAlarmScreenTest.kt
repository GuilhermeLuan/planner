package dev.guilhermeluan.planner.alarm

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
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class DoseAlarmScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = DoseAlarmUiState(
        dateText = "Quinta, 2 de outubro",
        timeText = "22:00",
        title = "Hora do Magnésio",
        reason = "2 comprimidos · lembrete às 21:30 ainda sem registro",
    )

    private fun show(
        onTake: () -> Unit = {},
        onSnooze: () -> Unit = {},
        onSkip: () -> Unit = {},
    ) = composeRule.setContent {
        PlannerTheme { DoseAlarmScreen(state, onTake, onSnooze, onSkip) }
    }

    @Test
    fun showsTheTimeTheMedicineTheDoseAndWhyTheAlarmRang() {
        show()

        composeRule.onNodeWithText("Quinta, 2 de outubro").assertIsDisplayed()
        composeRule.onNodeWithText("22:00").assertIsDisplayed()
        composeRule.onNodeWithText("Hora do Magnésio").assertIsDisplayed()
        composeRule.onNodeWithText("2 comprimidos · lembrete às 21:30 ainda sem registro").assertIsDisplayed()
    }

    @Test
    fun theThreeActionsCallTheirCallbacks() {
        val calls = mutableListOf<String>()
        show(onTake = { calls += "take" }, onSnooze = { calls += "snooze" }, onSkip = { calls += "skip" })

        composeRule.onNodeWithText("Tomei").performClick()
        composeRule.onNodeWithText("Adiar 10 min").performClick()
        composeRule.onNodeWithText("Pular dose").performClick()

        assertEquals(listOf("take", "snooze", "skip"), calls)
    }
}
