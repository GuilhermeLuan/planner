package dev.guilhermeluan.planner.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.guilhermeluan.planner.MainActivity
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.water.WaterDay
import dev.guilhermeluan.planner.water.WaterText

private const val ACTION_ADD_WATER_GLASS = "dev.guilhermeluan.planner.action.ADD_WATER_GLASS"

/** O Lembrete de água é único: tag e id fixos, sem chave de Dose na tag. */
private const val TAG = "water-reminder"
private const val NOTIFICATION_ID = 1

private fun reminderIntent(context: Context) =
    Intent(context, WaterReminderReceiver::class.java).setData(Uri.parse("planner://water/reminder"))

private fun dismissWaterReminder(context: Context) {
    context.getSystemService(NotificationManager::class.java).cancel(TAG, NOTIFICATION_ID)
}

/** Agenda o Lembrete de água no AlarmManager, exato quando a permissão existe. */
class AndroidWaterReminderGateway(private val context: Context) : WaterReminderGateway {
    override fun schedule(reminder: WaterReminder) {
        ReminderAlarmScheduler(AndroidReminderAlarmGateway(context, broadcast(context, reminderIntent(context))))
            .schedule(reminder.triggerAt.toEpochMilli())
    }

    override fun cancel() {
        cancelScheduled(context, reminderIntent(context))
    }

    override fun dismiss() {
        dismissWaterReminder(context)
    }
}

/** Entrega o Lembrete de água se a meta ainda não foi batida, e agenda o seguinte. */
class WaterReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync { (context.applicationContext as PlannerApplication).deliverWaterReminder() }
    }
}

/** "+ copo" soma um copo ao Consumo de hoje sem abrir o app, e tira o Lembrete da tela. */
class WaterReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADD_WATER_GLASS) return
        dismissWaterReminder(context)
        runAsync { (context.applicationContext as PlannerApplication).addWaterGlass() }
    }
}

/** A notificação do Lembrete de água, com o que falta da meta e o botão "+ copo". */
internal fun showWaterReminder(context: Context, day: WaterDay) {
    PlannerNotificationScheduler.ensureChannel(context)
    val notification = Notification.Builder(context, PlannerNotificationScheduler.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Hora de beber água")
        .setContentText(WaterText.remaining(day.consumedMl, day.goalMl))
        .setCategory(Notification.CATEGORY_REMINDER)
        .setContentIntent(openApp(context))
        .addAction(
            notificationAction(
                context,
                Intent(context, WaterReminderActionReceiver::class.java).setAction(ACTION_ADD_WATER_GLASS),
                "+ copo",
            ),
        )
        .setAutoCancel(true)
        .build()
    runCatching { context.getSystemService(NotificationManager::class.java).notify(TAG, NOTIFICATION_ID, notification) }
}

private fun openApp(context: Context) = activity(
    context,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)
