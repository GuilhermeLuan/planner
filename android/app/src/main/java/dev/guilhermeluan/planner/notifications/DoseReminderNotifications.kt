package dev.guilhermeluan.planner.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.guilhermeluan.planner.MainActivity
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.tasks.DoseKey
import dev.guilhermeluan.planner.ui.components.ClockFormatter

private const val EXTRA_NAME = "name"
private const val EXTRA_DOSE = "dose"
private const val ACTION_TAKE = "dev.guilhermeluan.planner.action.TAKE_DOSE"
private const val ACTION_SNOOZE = "dev.guilhermeluan.planner.action.SNOOZE_DOSE"

/** Os Lembretes dividem este id fixo e se distinguem pela tag, que é a chave da Dose. */
private const val NOTIFICATION_ID = 1

private fun doseUri(key: DoseKey) = doseUri("dose", key)

private fun reminderIntent(context: Context, key: DoseKey) =
    Intent(context, DoseReminderReceiver::class.java).setData(doseUri(key))

/** Agenda os Lembretes de Dose no AlarmManager, exatos quando a permissão existe. */
class AndroidDoseReminderGateway(private val context: Context) : DoseReminderGateway {
    private val scheduled = ScheduledKeys(context, "dose-reminders")

    override fun scheduledKeys(): Set<DoseKey> = scheduled.all()

    override fun schedule(reminder: DoseReminder) {
        val intent = reminderIntent(context, reminder.key)
            .putDoseKey(reminder.key)
            .putExtra(EXTRA_NAME, reminder.name)
            .putExtra(EXTRA_DOSE, reminder.dose)
        ReminderAlarmScheduler(AndroidReminderAlarmGateway(context, broadcast(context, intent)))
            .schedule(reminder.triggerAt.toEpochMilli())
        scheduled.add(reminder.key)
    }

    override fun cancel(key: DoseKey) {
        cancelScheduled(context, reminderIntent(context, key))
        scheduled.remove(key)
    }

    override fun dismiss(key: DoseKey) {
        context.getSystemService(NotificationManager::class.java).cancel(key.toString(), NOTIFICATION_ID)
    }
}

/** Entrega o Lembrete de Dose com as ações "Tomei" e "Adiar" e reagenda a janela seguinte. */
class DoseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.doseKey() ?: return
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val dose = intent.getStringExtra(EXTRA_DOSE).orEmpty()
        PlannerNotificationScheduler.ensureChannel(context)
        val notification = Notification.Builder(context, PlannerNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(name)
            .setContentText("Dose das ${key.time.format(ClockFormatter)} · $dose")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(openApp(context, key))
            .addAction(action(context, key, ACTION_TAKE, "Tomei"))
            .addAction(action(context, key, ACTION_SNOOZE, "Adiar"))
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(key.toString(), NOTIFICATION_ID, notification) }
        runAsync { (context.applicationContext as PlannerApplication).reconcileDoseSchedule() }
    }

    private fun openApp(context: Context, key: DoseKey) = activity(
        context,
        Intent(context, MainActivity::class.java).setData(doseUri(key)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

    private fun action(context: Context, key: DoseKey, action: String, label: String) = notificationAction(
        context,
        Intent(context, DoseReminderActionReceiver::class.java).setAction(action).setData(doseUri(key)).putDoseKey(key),
        label,
    )
}

/** "Tomei" registra a Dose e "Adiar" a lembra de novo em 10 minutos, sem abrir o app. */
class DoseReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.doseKey() ?: return
        val action = when (intent.action) {
            ACTION_TAKE -> DoseReminderAction.TAKE
            ACTION_SNOOZE -> DoseReminderAction.SNOOZE
            else -> return
        }
        context.getSystemService(NotificationManager::class.java).cancel(key.toString(), NOTIFICATION_ID)
        runAsync { (context.applicationContext as PlannerApplication).applyDoseReminderAction(key, action) }
    }
}

/** Reagenda Lembretes de Tarefas e Lembretes e Alarmes de Dose depois de reiniciar o celular, que apaga o que estava agendado. */
class DoseScheduleBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runAsync { (context.applicationContext as PlannerApplication).rescheduleAll() }
    }
}
