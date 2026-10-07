package dev.guilhermeluan.planner.medicines

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class ExactAlarmPermissionSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun explainsWhichAlarmRingsAndHowToAllowItWithTheMedicineAsExample() {
        composeRule.setContent {
            PlannerTheme {
                ExactAlarmPermissionSheet(
                    medicineName = "Vitamina D",
                    doseTime = LocalTime.of(13, 0),
                    alarmDelayMinutes = 30,
                    onOpenSettings = {},
                    onNotNow = {},
                )
            }
        }

        composeRule.onNodeWithTag("exact-alarm-sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Permitir alarmes").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Para tocar o alarme de Vitamina D às 13:30 se a dose das 13:00 continuar pendente, o Android precisa da permissão Alarmes e lembretes.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Toque em Abrir configurações").assertIsDisplayed()
        composeRule.onNodeWithText("Ative Permitir definir alarmes e lembretes").assertIsDisplayed()
        composeRule.onNodeWithText("Volte para o Planner").assertIsDisplayed()
        composeRule.onNodeWithText("Abrir configurações").assertIsDisplayed()
        composeRule.onNodeWithText("Agora não, usar só a notificação").assertIsDisplayed()
    }

    @Test
    fun dismissingTheSheetCountsAsNotNow() {
        var notNow = false
        composeRule.setContent {
            PlannerTheme {
                ExactAlarmPermissionSheet(
                    medicineName = "Vitamina D",
                    doseTime = LocalTime.of(13, 0),
                    alarmDelayMinutes = 30,
                    onOpenSettings = {},
                    onNotNow = { notNow = true },
                )
            }
        }

        composeRule.onNodeWithTag("exact-alarm-sheet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close sheet").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { notNow }

        assertTrue(notNow)
    }
}
