package dev.guilhermeluan.planner.notifications

import android.content.Context
import dev.guilhermeluan.planner.tasks.DoseKey

/**
 * Chaves agendadas no AlarmManager, que não lista o que tem, guardadas num SharedPreferences próprio. A
 * reconciliação dos Lembretes e dos Alarmes de Dose consulta isto para saber o que cancelar.
 */
internal class ScheduledKeys(context: Context, file: String) {
    private val preferences = context.getSharedPreferences(file, Context.MODE_PRIVATE)

    fun all(): Set<DoseKey> = synchronized(LOCK) { stored().mapNotNull { runCatching { DoseKey.parse(it) }.getOrNull() }.toSet() }

    fun add(key: DoseKey) = change { it + key.toString() }

    fun remove(key: DoseKey) = change { it - key.toString() }

    private fun stored(): Set<String> = preferences.getStringSet(SCHEDULED, emptySet()).orEmpty().toSet()

    private fun change(change: (Set<String>) -> Set<String>) = synchronized(LOCK) {
        preferences.edit().putStringSet(SCHEDULED, change(stored())).apply()
    }

    private companion object {
        const val SCHEDULED = "scheduled"
        val LOCK = Any()
    }
}
