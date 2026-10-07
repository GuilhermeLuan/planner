package dev.guilhermeluan.planner.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.guilhermeluan.planner.MainActivity
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.tasks.DoseKey

private const val ACTION_PREFIX = "dev.guilhermeluan.planner.action.ALARM_"

private fun ringIntent(context: Context, key: DoseKey) =
    Intent(context, DoseAlarmReceiver::class.java).setData(doseUri("dose-alarm", key)).putDoseKey(key)

/** O broadcast de Tomei, Adiar ou Pular do Alarme de Dose [key], da notificação ou da tela de alarme. */
internal fun alarmActionIntent(context: Context, key: DoseKey, action: DoseAlarmAction): Intent =
    Intent(context, DoseAlarmActionReceiver::class.java)
        .setAction(ACTION_PREFIX + action.name)
        .setData(doseUri("dose-alarm-action", key))
        .putDoseKey(key)

/**
 * Agenda os Alarmes de Dose com `setAlarmClock`, que o sistema nunca adia, e só com a permissão de alarme exato.
 * As chaves ficam guardadas para a reconciliação saber o que cancelar.
 */
class AndroidDoseAlarmGateway(private val context: Context) : DoseAlarmGateway {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val scheduled = ScheduledKeys(context, "dose-alarms")

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    override fun scheduledKeys(): Set<DoseKey> = scheduled.all()

    override fun schedule(alarm: DoseAlarm) {
        if (!canScheduleExactAlarms()) return
        val showApp = activity(
            context,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        manager.setAlarmClock(
            AlarmManager.AlarmClockInfo(alarm.triggerAt.toEpochMilli(), showApp),
            broadcast(context, ringIntent(context, alarm.key)),
        )
        scheduled.add(alarm.key)
    }

    override fun cancel(key: DoseKey) {
        cancelScheduled(context, ringIntent(context, key))
        scheduled.remove(key)
    }

    override fun dismiss(key: DoseKey) = DoseAlarmService.stopRinging(context, key)
}

/**
 * Dispara no horário do Alarme de Dose e só toca se a Dose ainda estiver pendente. A reconciliação cancela o
 * alarme quando a Dose é registrada antes; esta conferência cobre o alarme que já tinha sido entregue.
 */
class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.doseKey() ?: return
        runAsync { (context.applicationContext as PlannerApplication).ringDoseAlarm(key) }
    }
}

/** Tomei, Adiar e Pular dose, pela notificação ou pela tela do alarme. O som para antes de gravar a ação. */
class DoseAlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.doseKey() ?: return
        val action = DoseAlarmAction.entries.firstOrNull { ACTION_PREFIX + it.name == intent.action } ?: return
        DoseAlarmService.stopRinging(context, key)
        runAsync { (context.applicationContext as PlannerApplication).applyDoseAlarmAction(key, action) }
    }
}
