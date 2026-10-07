package dev.guilhermeluan.planner.alarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.guilhermeluan.planner.notifications.DoseAlarmAction
import dev.guilhermeluan.planner.notifications.DoseAlarmService
import dev.guilhermeluan.planner.notifications.RingingDoseAlarm
import dev.guilhermeluan.planner.notifications.alarmActionIntent
import dev.guilhermeluan.planner.notifications.ringingAlarm
import dev.guilhermeluan.planner.tasks.DoseKey
import dev.guilhermeluan.planner.ui.theme.PlannerTheme

/**
 * Tela cheia do Alarme de Dose: aparece sobre o bloqueio e liga a tela. Um segundo alarme chega por
 * [onNewIntent] e toma o lugar do primeiro, que segue tocando na própria notificação. A tela fecha quando o
 * alarme mostrado acaba por outro caminho: resposta pela notificação, Dose registrada no app ou silêncio.
 */
class DoseAlarmActivity : ComponentActivity() {
    private var alarm by mutableStateOf<RingingDoseAlarm?>(null)
    private val closeWhenEnded: (DoseKey) -> Unit = { key -> if (key == alarm?.key) finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        alarm = intent.ringingAlarm() ?: return finish()
        DoseAlarmService.addEndedListener(closeWhenEnded)
        setContent {
            val current = alarm ?: return@setContent
            PlannerTheme {
                DoseAlarmScreen(
                    DoseAlarmUiState.of(current),
                    onTake = { answer(current.key, DoseAlarmAction.TAKE) },
                    onSnooze = { answer(current.key, DoseAlarmAction.SNOOZE) },
                    onSkip = { answer(current.key, DoseAlarmAction.SKIP) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.ringingAlarm()?.let { alarm = it }
    }

    override fun onDestroy() {
        DoseAlarmService.removeEndedListener(closeWhenEnded)
        super.onDestroy()
    }

    /** A ação vai pelo mesmo receiver da notificação, que para o som e grava a resposta fora da tela. */
    private fun answer(key: DoseKey, action: DoseAlarmAction) {
        sendBroadcast(alarmActionIntent(this, key, action))
        finish()
    }
}
