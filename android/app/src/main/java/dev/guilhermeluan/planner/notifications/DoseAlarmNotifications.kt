package dev.guilhermeluan.planner.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import dev.guilhermeluan.planner.alarm.DoseAlarmActivity
import dev.guilhermeluan.planner.tasks.DoseKey
import dev.guilhermeluan.planner.ui.components.ClockFormatter
import java.time.LocalDateTime

private const val EXTRA_NAME = "name"
private const val EXTRA_DOSE = "dose"
private const val EXTRA_RING_AT = "ringAt"

/** Os Alarmes de Dose fora do serviço dividem este id e se distinguem pela tag, que é a chave da Dose. */
internal const val ALARM_NOTIFICATION_ID = 2

/** Canal próprio dos Alarmes de Dose, de importância alta. O som contínuo vem do serviço, não do canal. */
const val ALARM_CHANNEL_ID = "planner-dose-alarms"

/** Canal de reserva, com som de alarme, para quando o serviço não consegue tocar o som contínuo. */
const val ALARM_SOUND_CHANNEL_ID = "planner-dose-alarms-sound"

/** O texto do botão "Adiar" do Alarme de Dose, que acompanha [DoseScheduleCoordinator.SNOOZE]. */
internal val SNOOZE_LABEL = "Adiar ${DoseScheduleCoordinator.SNOOZE.toMinutes()} min"

/** O título do Alarme de Dose, na tela e na notificação. */
internal val RingingDoseAlarm.title: String get() = "Hora do $name"

/** Por que o Alarme de Dose tocou, na tela e na notificação. */
internal val RingingDoseAlarm.reason: String
    get() = "$dose · lembrete às ${doseTime.format(ClockFormatter)} ainda sem registro"

/** Os sons que o alarme tenta, em ordem, quando o aparelho não tem toque de alarme. */
internal val AlarmSounds: List<Uri>
    get() = listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)
        .mapNotNull(RingtoneManager::getDefaultUri)

internal val AlarmAudio: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

internal fun ensureAlarmChannels(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannels(
        listOf(
            NotificationChannel(ALARM_CHANNEL_ID, "Alarmes de dose", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarme que toca quando uma dose continua pendente"
                setSound(null, null)
                enableVibration(true)
            },
            NotificationChannel(ALARM_SOUND_CHANNEL_ID, "Alarmes de dose (reserva)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Usado quando o alarme não consegue tocar com som contínuo"
                setSound(AlarmSounds.firstOrNull(), AlarmAudio)
                enableVibration(true)
            },
        ),
    )
}

/** O Intent que abre a tela do alarme, com o que ela mostra. */
internal fun Intent.putRingingAlarm(alarm: RingingDoseAlarm): Intent = this
    .putDoseKey(alarm.key)
    .putExtra(EXTRA_NAME, alarm.name)
    .putExtra(EXTRA_DOSE, alarm.dose)
    .putExtra(EXTRA_RING_AT, alarm.ringAt.toString())

/** O Alarme de Dose que veio no Intent; nulo se faltar algo. */
internal fun Intent.ringingAlarm(): RingingDoseAlarm? {
    val key = doseKey() ?: return null
    val ringAt = getStringExtra(EXTRA_RING_AT)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return null
    return RingingDoseAlarm(key, getStringExtra(EXTRA_NAME).orEmpty(), getStringExtra(EXTRA_DOSE).orEmpty(), ringAt)
}

internal fun NotificationManager.cancelAlarm(key: DoseKey) = cancel(key.toString(), ALARM_NOTIFICATION_ID)

internal fun NotificationManager.notifyAlarm(key: DoseKey, notification: Notification) {
    runCatching { notify(key.toString(), ALARM_NOTIFICATION_ID, notification) }
}

/**
 * A notificação do Alarme de Dose. Enquanto [ongoing], fica presa à tela; depois, some ao toque. Com [fullScreen] e a
 * permissão de tela cheia, abre a tela de alarme sobre o bloqueio; sem a permissão, fica só a notificação de alta
 * prioridade.
 */
internal fun alarmNotification(
    context: Context,
    alarm: RingingDoseAlarm,
    ongoing: Boolean,
    fullScreen: Boolean = false,
    channel: String = ALARM_CHANNEL_ID,
): Notification {
    val screen = activity(
        context,
        Intent(context, DoseAlarmActivity::class.java)
            .setData(doseUri("dose-alarm-screen", alarm.key))
            .putRingingAlarm(alarm)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
    val builder = Notification.Builder(context, channel)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(alarm.title)
        .setContentText(alarm.reason)
        .setCategory(Notification.CATEGORY_ALARM)
        .setContentIntent(screen)
        .setOngoing(ongoing)
        .setAutoCancel(!ongoing)
        .addAction(notificationAction(context, alarmActionIntent(context, alarm.key, DoseAlarmAction.TAKE), "Tomei"))
        .addAction(notificationAction(context, alarmActionIntent(context, alarm.key, DoseAlarmAction.SNOOZE), SNOOZE_LABEL))
        .addAction(notificationAction(context, alarmActionIntent(context, alarm.key, DoseAlarmAction.SKIP), "Pular dose"))
    if (fullScreen && ExactAlarmPermission.canUseFullScreenIntent(context)) builder.setFullScreenIntent(screen, true)
    return builder.build()
}
