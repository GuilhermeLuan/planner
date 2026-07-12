package dev.guilhermeluan.planner.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ServerConfigurationStoreTest {
    @Test
    fun `configured server is available on the next app start`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferencesName = "server-config-${UUID.randomUUID()}"
        try {
            val firstStart = ServerConfigurationStore(context, preferencesName)
            firstStart.save(ServerConfiguration.parse("https://planner.example.com"))

            val nextStart = ServerConfigurationStore(context, preferencesName)

            assertEquals("https://planner.example.com", nextStart.read()?.baseUrl)
        } finally {
            context.deleteSharedPreferences(preferencesName)
        }
    }
}
