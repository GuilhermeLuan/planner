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
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.guilhermeluan.planner.diagnostics.PlannerDiagnostics
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
        val logger = PlannerDiagnostics.logger(context)
        logger.log("reminder_schedule_requested", task.id, mapOf("timezone" to timezone))
        val time = task.time ?: run {
            logger.log("reminder_skipped_without_time", task.id)
            cancelTask(context, task.id)
            return
        }
        val trigger = ReminderPlanner.triggerAt(task.day, time, timezone, clock) ?: run {
            logger.log("reminder_skipped_past", task.id)
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
        val mode = ReminderAlarmScheduler(AndroidReminderAlarmGateway(context, pendingIntent))
            .schedule(trigger.toEpochMilli())
        logger.log(
            if (mode == ReminderAlarmMode.EXACT) "reminder_scheduled_exact" else "reminder_scheduled_inexact",
            task.id,
            mapOf("triggerAt" to trigger.toString(), "timezone" to timezone),
        )
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
        PlannerDiagnostics.logger(context).log("reminder_cancelled", taskId)
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
    fun schedule(triggerAtMillis: Long): ReminderAlarmMode =
        if (gateway.canScheduleExactAlarms()) {
            gateway.scheduleExact(triggerAtMillis)
            ReminderAlarmMode.EXACT
        } else {
            gateway.scheduleInexact(triggerAtMillis)
            ReminderAlarmMode.INEXACT
        }
}

enum class ReminderAlarmMode { EXACT, INEXACT }

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
        val logger = PlannerDiagnostics.logger(context)
        val subjectId = intent.getStringExtra("id")
        logger.log("reminder_receiver_started", subjectId)
        PlannerNotificationScheduler.ensureChannel(context)
        val title = intent.getStringExtra("title") ?: "Lembrete do Planner"
        val id = intent.getStringExtra("id")?.hashCode() ?: title.hashCode()
        val manager = context.getSystemService(NotificationManager::class.java)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED || !manager.areNotificationsEnabled()) {
            logger.log("notification_blocked_permission", subjectId)
            return
        }
        if (manager.getNotificationChannel(PlannerNotificationScheduler.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            logger.log("notification_blocked_channel", subjectId)
            return
        }
        val notification = Notification.Builder(
            context,
            PlannerNotificationScheduler.CHANNEL_ID,
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Planner")
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
            logger.log("notification_published", subjectId)
        } catch (error: Exception) {
            logger.log("notification_failed", subjectId, mapOf(
                "exception" to error.javaClass.simpleName,
                "message" to (error.message ?: "unknown").take(160),
            ))
        }
    }
}
