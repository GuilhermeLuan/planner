package dev.guilhermeluan.planner.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.guilhermeluan.planner.tasks.DoseKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A chave da Dose, como vai nos Intents dos Lembretes e dos Alarmes de Dose. */
internal const val EXTRA_KEY = "doseKey"

internal fun Intent.putDoseKey(key: DoseKey): Intent = putExtra(EXTRA_KEY, key.toString())

internal fun Intent.doseKey(): DoseKey? = getStringExtra(EXTRA_KEY)?.let { runCatching { DoseKey.parse(it) }.getOrNull() }

/**
 * Cada Lembrete e cada Alarme de Dose é identificado pela própria chave, nunca por um hash dela: a chave vai no
 * `data` dos Intents, o que distingue um PendingIntent de outro. [authority] separa os usos de uma mesma chave.
 */
internal fun doseUri(authority: String, key: DoseKey): Uri =
    Uri.Builder().scheme("planner").authority(authority).appendPath(key.toString()).build()

internal fun broadcast(context: Context, intent: Intent): PendingIntent =
    PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

internal fun activity(context: Context, intent: Intent): PendingIntent =
    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

/** Cancela no AlarmManager o que foi agendado com [intent], se algo foi. */
internal fun cancelScheduled(context: Context, intent: Intent) {
    PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        ?.let { pendingIntent ->
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            pendingIntent.cancel()
        }
}

/** Botão de notificação que entrega [intent] a um receiver. */
internal fun notificationAction(context: Context, intent: Intent, label: String): Notification.Action =
    Notification.Action.Builder(null, label, broadcast(context, intent)).build()

internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
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
