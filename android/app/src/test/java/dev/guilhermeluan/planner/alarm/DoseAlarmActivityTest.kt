package dev.guilhermeluan.planner.alarm

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.notifications.DoseAlarmActionReceiver
import dev.guilhermeluan.planner.notifications.DoseAlarmService
import dev.guilhermeluan.planner.notifications.RingingDoseAlarm
import dev.guilhermeluan.planner.notifications.putRingingAlarm
import dev.guilhermeluan.planner.tasks.DoseKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// Sem fixar o SDK 34: esta é a primeira classe da suíte, e o Robolectric 4.16 às vezes não carrega o runtime
// nativo do SDK padrão (FileSystemAlreadyExistsException) quando o sandbox do 34 sobe antes dele.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class DoseAlarmActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val magnesium = alarm("magnesio", "Magnésio", LocalTime.of(21, 30))
    private val vitamin = alarm("vitamina", "Vitamina D", LocalTime.of(21, 45))

    private fun alarm(id: String, name: String, time: LocalTime) = RingingDoseAlarm(
        key = DoseKey(id, LocalDate.of(2026, 10, 1), time),
        name = name,
        dose = "1 cápsula",
        ringAt = LocalDateTime.of(2026, 10, 1, 22, 15),
    )

    private fun intentOf(alarm: RingingDoseAlarm) = Intent(context, DoseAlarmActivity::class.java).putRingingAlarm(alarm)

    @Test
    fun showsTheAlarmFromTheIntent() {
        Robolectric.buildActivity(DoseAlarmActivity::class.java, intentOf(magnesium)).setup()

        composeRule.onNodeWithText("Quinta-feira, 1 de outubro").assertExists()
        composeRule.onNodeWithText("22:15").assertExists()
        composeRule.onNodeWithText("Hora do Magnésio").assertExists()
        composeRule.onNodeWithText("1 cápsula · lembrete às 21:30 ainda sem registro").assertExists()
    }

    @Test
    fun aSecondAlarmReplacesTheFirstOnScreen() {
        val controller = Robolectric.buildActivity(DoseAlarmActivity::class.java, intentOf(magnesium)).setup()

        controller.newIntent(intentOf(vitamin))

        composeRule.onNodeWithText("Hora do Vitamina D").assertExists()
        composeRule.onNodeWithText("Hora do Magnésio").assertDoesNotExist()
    }

    @Test
    fun theScreenClosesWhenItsAlarmEndsElsewhere() {
        val controller = Robolectric.buildActivity(DoseAlarmActivity::class.java, intentOf(magnesium)).setup()

        DoseAlarmService.stopRinging(context, vitamin.key)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("outro alarme não fecha a tela", controller.get().isFinishing)

        DoseAlarmService.stopRinging(context, magnesium.key)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun anAnswerGoesToTheActionReceiverForTheAlarmOnScreenAndClosesTheScreen() {
        val controller = Robolectric.buildActivity(DoseAlarmActivity::class.java, intentOf(magnesium)).setup()
        controller.newIntent(intentOf(vitamin))

        composeRule.onNodeWithText("Tomei").performClick()

        val sent = shadowOf(context as android.app.Application).broadcastIntents.single()
        assertEquals(DoseAlarmActionReceiver::class.java.name, sent.component?.className)
        assertEquals(vitamin.key.toString(), sent.getStringExtra("doseKey"))
        assertTrue(controller.get().isFinishing)
    }
}
