package dev.guilhermeluan.planner.diagnostics

import android.content.Context
import java.time.Clock
import java.util.UUID

object PlannerDiagnostics {
    @Volatile private var instance: FileDiagnosticLogger? = null

    fun logger(context: Context): FileDiagnosticLogger = instance ?: synchronized(this) {
        instance ?: run {
            val prefs = context.getSharedPreferences("planner-diagnostics", Context.MODE_PRIVATE)
            val secret = prefs.getString("installation-secret", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("installation-secret", it).apply()
            }
            FileDiagnosticLogger(context.filesDir.resolve("diagnostics"), Clock.systemUTC(), secret).also { instance = it }
        }
    }
}
