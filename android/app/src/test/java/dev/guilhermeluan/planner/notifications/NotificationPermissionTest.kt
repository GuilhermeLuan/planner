package dev.guilhermeluan.planner.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationPermissionTest {
    @Test
    @Config(sdk = [32])
    fun `no request needed below API 33`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(NotificationPermission.shouldRequest(context))
    }

    @Test
    @Config(sdk = [33])
    fun `request needed on API 33 when permission not granted`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(NotificationPermission.shouldRequest(context))
    }
}
