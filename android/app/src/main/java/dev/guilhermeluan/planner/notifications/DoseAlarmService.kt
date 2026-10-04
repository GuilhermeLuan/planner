package dev.guilhermeluan.planner.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.guilhermeluan.planner.tasks.DoseKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/** A notificação do serviço, sem tag: é sempre a do alarme que tocou por último. */
private const val FOREGROUND_NOTIFICATION_ID = 3

/**
 * Foreground service `systemExempted` que toca o som de alarme em loop e mantém a notificação com full-screen
 * intent. Vários Alarmes de Dose podem tocar juntos: o último ocupa a notificação do serviço e os outros seguem
 * cada um na sua. Cada alarme cala sozinho [SILENCE_AFTER_MS] depois de começar, deixando a notificação visível e
 * a Dose pendente; o som para quando não resta nenhum. Um WakeLock mantém o som e esses prazos com a tela
 * bloqueada. Sem nenhum toque que funcione, a notificação vai para o canal de reserva, que tem som próprio.
 */
class DoseAlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    /** Os alarmes tocando, na ordem em que chegaram; o último é o da notificação do serviço. */
    private val ringingAlarms = linkedMapOf<DoseKey, RingingDoseAlarm>()
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastStartId = 0

    private val notifications get() = getSystemService(NotificationManager::class.java)

    /** Com o som contínuo tocando, o canal fica mudo; sem ele, o canal de reserva toca o som. */
    private val channel get() = if (player != null) ALARM_CHANNEL_ID else ALARM_SOUND_CHANNEL_ID

    private val foregroundAlarm get() = ringingAlarms.values.lastOrNull()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        ensureAlarmChannels(this)
        val alarm = intent?.ringingAlarm()
        alarm?.let { starting.remove(it.key) }
        if (alarm == null || cancelled.remove(alarm.key)) {
            // Quem chamou startForegroundService espera startForeground, mesmo sem nada para tocar.
            if (ringingAlarms.isEmpty()) {
                startInForeground(Notification.Builder(this, ALARM_CHANNEL_ID).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).build())
                finishRinging()
            }
            return START_NOT_STICKY
        }
        if (player == null) player = startSound()
        foregroundAlarm?.takeIf { it.key != alarm.key }?.let { previous ->
            notifications.notifyAlarm(previous.key, alarmNotification(this, previous, ongoing = true, channel = channel))
        }
        ringingAlarms.remove(alarm.key)?.let { handler.removeCallbacksAndMessages(it.key) }
        ringingAlarms[alarm.key] = alarm
        notifications.cancelAlarm(alarm.key)
        startInForeground(alarmNotification(this, alarm, ongoing = true, fullScreen = true, channel = channel))
        keepAwake()
        handler.postAtTime({ silence(alarm.key) }, alarm.key, SystemClock.uptimeMillis() + SILENCE_AFTER_MS)
        return START_NOT_STICKY
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun isRinging(key: DoseKey) = key in ringingAlarms

    /** [SILENCE_AFTER_MS] sem resposta: o alarme cala e a notificação fica, agora dispensável. */
    private fun silence(key: DoseKey) {
        val alarm = end(key) ?: return
        notifications.notifyAlarm(key, alarmNotification(this, alarm, ongoing = false))
    }

    /**
     * Tira [key] dos alarmes tocando, porque foi respondido, registrado ou calou. Sem mais nenhum, o serviço
     * termina; senão o anterior volta ao serviço.
     */
    private fun end(key: DoseKey): RingingDoseAlarm? {
        val wasForeground = foregroundAlarm?.key == key
        val alarm = ringingAlarms.remove(key) ?: return null
        handler.removeCallbacksAndMessages(alarm.key)
        notifications.cancelAlarm(key)
        val next = foregroundAlarm
        if (next == null) {
            finishRinging()
        } else if (wasForeground) {
            notifications.cancelAlarm(next.key)
            startInForeground(alarmNotification(this, next, ongoing = true, channel = channel))
        }
        announceEnded(key)
        return alarm
    }

    /** Para o som e sai do primeiro plano. Só termina se nenhum outro alarme chegou nesse meio-tempo. */
    private fun finishRinging() {
        stopSound()
        wakeLock?.takeIf { it.isHeld }?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    private fun keepAwake() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "planner:dose-alarm")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(SILENCE_AFTER_MS + WAKE_LOCK_MARGIN_MS)
    }

    /** O primeiro dos [AlarmSounds] que conseguir tocar em loop, ou nulo se nenhum tocar. */
    private fun startSound(): MediaPlayer? = AlarmSounds.firstNotNullOfOrNull { sound ->
        val player = MediaPlayer()
        runCatching {
            player.setAudioAttributes(AlarmAudio)
            player.setDataSource(this, sound)
            player.isLooping = true
            player.prepare()
            player.start()
            player
        }.onFailure { player.release() }.getOrNull()
    }

    private fun stopSound() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        wakeLock?.takeIf { it.isHeld }?.release()
        if (running === this) running = null
        super.onDestroy()
    }

    companion object {
        /** Quanto cada alarme toca sem resposta antes de calar sozinho. */
        const val SILENCE_AFTER_MS = 10 * 60 * 1000L

        private const val WAKE_LOCK_MARGIN_MS = 60 * 1000L

        private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

        /** O serviço em execução; só é lido e escrito na thread principal. */
        private var running: DoseAlarmService? = null

        /** Alarmes pedidos ao sistema que o serviço ainda não recebeu, e os dispensados nesse intervalo. */
        private val starting = ConcurrentHashMap.newKeySet<DoseKey>()
        private val cancelled = ConcurrentHashMap.newKeySet<DoseKey>()

        private val endedListeners = CopyOnWriteArraySet<(DoseKey) -> Unit>()

        /** Avisa, na thread principal, quando um Alarme de Dose sai da tela; a tela de alarme fecha junto. */
        fun addEndedListener(listener: (DoseKey) -> Unit) {
            endedListeners += listener
        }

        fun removeEndedListener(listener: (DoseKey) -> Unit) {
            endedListeners -= listener
        }

        private fun announceEnded(key: DoseKey) = endedListeners.forEach { it(key) }

        /**
         * Toca o Alarme de Dose. Se o sistema não deixar iniciar o serviço, o alarme sai como notificação de
         * prioridade alta no canal de reserva, que tem som de alarme.
         */
        fun start(context: Context, alarm: RingingDoseAlarm) {
            starting += alarm.key
            val intent = Intent(context, DoseAlarmService::class.java).putRingingAlarm(alarm)
            runCatching { ContextCompat.startForegroundService(context, intent) }.onFailure {
                starting -= alarm.key
                ensureAlarmChannels(context)
                context.getSystemService(NotificationManager::class.java).notifyAlarm(
                    alarm.key,
                    alarmNotification(context, alarm, ongoing = false, fullScreen = true, channel = ALARM_SOUND_CHANNEL_ID),
                )
            }
        }

        /** Para o som e tira da tela o Alarme de Dose [key], se ele estiver tocando, a caminho ou visível. */
        fun stopRinging(context: Context, key: DoseKey) {
            context.getSystemService(NotificationManager::class.java).cancelAlarm(key)
            mainHandler.post {
                val service = running
                if (service != null && service.isRinging(key)) {
                    service.end(key)
                } else {
                    // Pedido ao sistema, mas o serviço ainda não começou: ele descarta o alarme quando começar.
                    if (key in starting) cancelled += key
                    announceEnded(key)
                }
            }
        }
    }
}
