package dev.guilhermeluan.planner.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.guilhermeluan.planner.MainActivity
import dev.guilhermeluan.planner.PlannerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val EXTRA_KEY = "doseKey"
private const val EXTRA_NAME = "name"
private const val EXTRA_DOSE = "dose"
private const val EXTRA_TIME = "time"
private const val ACTION_TAKE = "dev.guilhermeluan.planner.action.TAKE_DOSE"
private const val ACTION_SNOOZE = "dev.guilhermeluan.planner.action.SNOOZE_DOSE"

/**
 * Cada Lembrete é identificado pela própria chave, nunca por um hash dela: a chave vai como `data` dos Intents
 * (o que distingue um PendingIntent de outro) e como tag da notificação, que divide o mesmo id fixo.
 */
private const val NOTIFICATION_ID = 1

private fun doseUri(key: String): Uri = Uri.Builder().scheme("planner").authority("dose").appendPath(key).build()

private fun reminderIntent(context: Context, key: String) =
    Intent(context, DoseReminderReceiver::class.java).setData(doseUri(key))

/**
 * Agenda os Lembretes de Dose no AlarmManager, exatos quando a permissão existe. O AlarmManager não lista o que
 * está agendado, então as chaves ficam guardadas aqui para a reconciliação saber o que cancelar.
 */
class AndroidDoseReminderGateway(private val context: Context) : DoseReminderGateway {
    private val preferences = context.getSharedPreferences("dose-reminders", Context.MODE_PRIVATE)

    override fun scheduledKeys(): Set<String> = synchronized(LOCK) {
        preferences.getStringSet(SCHEDULED, emptySet()).orEmpty().toSet()
    }

    override fun schedule(reminder: DoseReminder) {
        val intent = reminderIntent(context, reminder.key)
            .putExtra(EXTRA_KEY, reminder.key)
            .putExtra(EXTRA_NAME, reminder.name)
            .putExtra(EXTRA_DOSE, reminder.dose)
            .putExtra(EXTRA_TIME, reminder.time.toString())
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        ReminderAlarmScheduler(AndroidReminderAlarmGateway(context, pendingIntent))
            .schedule(reminder.triggerAt.toEpochMilli())
        update { it + reminder.key }
    }

    override fun cancel(key: String) {
        PendingIntent.getBroadcast(
            context,
            0,
            reminderIntent(context, key),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { pendingIntent ->
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            pendingIntent.cancel()
        }
        update { it - key }
    }

    override fun dismiss(key: String) {
        context.getSystemService(NotificationManager::class.java).cancel(key, NOTIFICATION_ID)
    }

    private fun update(change: (Set<String>) -> Set<String>) = synchronized(LOCK) {
        val current = preferences.getStringSet(SCHEDULED, emptySet()).orEmpty().toSet()
        preferences.edit().putStringSet(SCHEDULED, change(current)).apply()
    }

    private companion object {
        const val SCHEDULED = "scheduled"
        val LOCK = Any()
    }
}

/** Entrega o Lembrete de Dose com as ações "Tomei" e "Adiar" e reagenda a janela seguinte. */
class DoseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val dose = intent.getStringExtra(EXTRA_DOSE).orEmpty()
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty()
        PlannerNotificationScheduler.ensureChannel(context)
        val notification = Notification.Builder(context, PlannerNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(name)
            .setContentText("Dose das $time · $dose")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(openApp(context, key))
            .addAction(action(context, key, ACTION_TAKE, "Tomei"))
            .addAction(action(context, key, ACTION_SNOOZE, "Adiar"))
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(key, NOTIFICATION_ID, notification) }
        runAsync { (context.applicationContext as PlannerApplication).reconcileMedicineReminders() }
    }

    private fun openApp(context: Context, key: String): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).setData(doseUri(key)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, key: String, action: String, label: String): Notification.Action {
        val intent = Intent(context, DoseReminderActionReceiver::class.java)
            .setAction(action)
            .setData(doseUri(key))
            .putExtra(EXTRA_KEY, key)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(null, label, pendingIntent).build()
    }
}

/** "Tomei" registra a Dose e "Adiar" a lembra de novo em 10 minutos, sem abrir o app. */
class DoseReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val action = when (intent.action) {
            ACTION_TAKE -> DoseReminderAction.TAKE
            ACTION_SNOOZE -> DoseReminderAction.SNOOZE
            else -> return
        }
        context.getSystemService(NotificationManager::class.java).cancel(key, NOTIFICATION_ID)
        runAsync { (context.applicationContext as PlannerApplication).applyDoseReminderAction(key, action) }
    }
}

/** Reconstrói os Lembretes de Dose depois de reiniciar o celular, que apaga os alarmes agendados. */
class DoseRemindersBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runAsync { (context.applicationContext as PlannerApplication).reconcileMedicineReminders() }
    }
}

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    // Nulo quando o receiver não foi chamado pelo sistema (por exemplo, em testes).
    val pending: BroadcastReceiver.PendingResult? = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending?.finish()
        }
    }
}
