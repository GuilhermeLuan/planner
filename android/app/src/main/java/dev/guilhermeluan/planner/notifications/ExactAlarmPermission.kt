package dev.guilhermeluan.planner.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.guilhermeluan.planner.PlannerApplication

/** As permissões de alarme exato e de tela cheia que o Alarme de Dose usa (ADR 0024). */
object ExactAlarmPermission {
    /** Se o app pode agendar alarmes exatos (Android < 12 sempre pode). */
    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Se o app pode abrir a tela de alarme em tela cheia (Android < 14 sempre pode). */
    fun canUseFullScreenIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** A tela "Alarmes e lembretes" do app; abaixo do Android 12, a página de detalhes do app. */
    fun settingsIntent(context: Context): Intent {
        val action = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        } else {
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
        }
        return Intent(action)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Reagenda Lembretes e Alarmes quando a permissão de alarme exato muda. */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        runAsync { (context.applicationContext as PlannerApplication).rescheduleAll() }
    }
}
