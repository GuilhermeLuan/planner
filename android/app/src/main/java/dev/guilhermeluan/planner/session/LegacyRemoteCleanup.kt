package dev.guilhermeluan.planner.session

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore

class LegacyRemoteCleanup(private val context: Context) {
    fun clearServerConfiguration() {
        context.getSharedPreferences("planner-server-configuration", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    suspend fun clearProtectedSession() = withContext(Dispatchers.IO) {
        context.getSharedPreferences("planner-protected-session", Context.MODE_PRIVATE)
            .edit().clear().commit()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry("planner-session-key")
        }
        Unit
    }
}
