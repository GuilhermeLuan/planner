package dev.guilhermeluan.planner.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.guilhermeluan.planner.tasks.PlannerTask
import dev.guilhermeluan.planner.tasks.TaskStatus
import java.time.Clock

interface ReminderAdapter {
    fun reconcile(task: PlannerTask, timezone: String)
    fun cancel(taskId: String)
    fun rebuild(tasks: List<PlannerTask>, timezone: String)
}

class AndroidReminderAdapter(private val context: Context) : ReminderAdapter {
    override fun reconcile(task: PlannerTask, timezone: String) =
        PlannerNotificationScheduler.scheduleTask(context, task, timezone)

    override fun cancel(taskId: String) = PlannerNotificationScheduler.cancelTask(context, taskId)

    override fun rebuild(tasks: List<PlannerTask>, timezone: String) =
        PlannerNotificationScheduler.rebuild(context, tasks, timezone)
}

object PlannerNotificationScheduler {
    const val CHANNEL_ID = "planner-reminders"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_ID = "id"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Lembretes do Planner",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Tarefas e ocorrências com horário" },
        )
    }

    fun scheduleTask(
        context: Context,
        task: PlannerTask,
        timezone: String,
        clock: Clock = Clock.systemUTC(),
    ) {
        val time = task.time ?: run {
            cancelTask(context, task.id)
            return
        }
        val trigger = ReminderPlanner.triggerAt(task.day, time, timezone, clock) ?: run {
            cancelTask(context, task.id)
            return
        }
        ensureChannel(context)
        val intent = Intent(context, PlannerReminderReceiver::class.java)
            .putExtra(EXTRA_ID, task.id)
            .putExtra(EXTRA_TITLE, task.title)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        ReminderAlarmScheduler(AndroidReminderAlarmGateway(context, pendingIntent))
            .schedule(trigger.toEpochMilli())
    }

    fun cancelTask(context: Context, taskId: String) {
        val intent = Intent(context, PlannerReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun rebuild(
        context: Context,
        tasks: List<PlannerTask>,
        timezone: String,
        clock: Clock = Clock.systemUTC(),
    ) {
        tasks.forEach { task ->
            if (task.status == TaskStatus.DONE || task.archived) {
                cancelTask(context, task.id)
            } else {
                scheduleTask(context, task, timezone, clock)
            }
        }
    }
}

interface ReminderAlarmGateway {
    fun canScheduleExactAlarms(): Boolean
    fun scheduleExact(triggerAtMillis: Long)
    fun scheduleInexact(triggerAtMillis: Long)
}

class ReminderAlarmScheduler(private val gateway: ReminderAlarmGateway) {
    fun schedule(triggerAtMillis: Long) {
        if (gateway.canScheduleExactAlarms()) {
            gateway.scheduleExact(triggerAtMillis)
        } else {
            gateway.scheduleInexact(triggerAtMillis)
        }
    }
}

private class AndroidReminderAlarmGateway(
    context: Context,
    private val pendingIntent: PendingIntent,
) : ReminderAlarmGateway {
    private val manager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    override fun scheduleExact(triggerAtMillis: Long) {
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    override fun scheduleInexact(triggerAtMillis: Long) {
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}

class PlannerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlannerNotificationScheduler.ensureChannel(context)
        val title = intent.getStringExtra("title") ?: "Lembrete do Planner"
        val id = intent.getStringExtra("id")?.hashCode() ?: title.hashCode()
        val notification = Notification.Builder(
            context,
            PlannerNotificationScheduler.CHANNEL_ID,
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Planner")
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(id, notification)
        }
    }
}
