package dev.guilhermeluan.planner.widget

import android.content.Context
import java.time.Instant

/** Guarda a confirmação de cada botão do widget (qual e até quando), para sobreviver à recriação do widget. */
class QuickConfirmationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("quick_widget", Context.MODE_PRIVATE)

    fun save(confirmation: QuickConfirmation) {
        val k = confirmation.kind.name
        prefs.edit()
            .putString("$k.title", confirmation.title)
            .putString("$k.detail", confirmation.detail)
            .putString("$k.description", confirmation.description)
            .putLong("$k.until", confirmation.until.toEpochMilli())
            .apply()
    }

    /** A confirmação de [kind] que ainda vale em [now]; nula se não há ou se já expirou. */
    fun active(kind: QuickKind, now: Instant): QuickConfirmation? {
        val k = kind.name
        val until = prefs.getLong("$k.until", 0L).takeIf { it > 0 } ?: return null
        val confirmation = QuickConfirmation(
            kind = kind,
            title = prefs.getString("$k.title", null) ?: return null,
            detail = prefs.getString("$k.detail", null) ?: return null,
            description = prefs.getString("$k.description", null) ?: return null,
            until = Instant.ofEpochMilli(until),
        )
        return confirmation.takeIf { it.isActive(now) }
    }
}
