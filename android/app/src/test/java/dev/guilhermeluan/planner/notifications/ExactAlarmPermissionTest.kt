package dev.guilhermeluan.planner.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IInterface
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowServiceManager
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExactAlarmPermissionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() = restoreNotificationService()

    @Test
    fun exactAlarmsFollowTheSystemPermission() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(ExactAlarmPermission.canScheduleExactAlarms(context))

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(ExactAlarmPermission.canScheduleExactAlarms(context))
    }

    @Test
    @Config(sdk = [30])
    fun exactAlarmsAreAlwaysAllowedBeforeAndroid12() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertTrue(ExactAlarmPermission.canScheduleExactAlarms(context))
    }

    @Test
    fun fullScreenFollowsTheSystemPermission() {
        grantFullScreenIntent(true)
        assertTrue(ExactAlarmPermission.canUseFullScreenIntent(context))

        grantFullScreenIntent(false)
        assertFalse(ExactAlarmPermission.canUseFullScreenIntent(context))
    }

    @Test
    @Config(sdk = [33])
    fun fullScreenIsAlwaysAllowedBeforeAndroid14() {
        assertTrue(ExactAlarmPermission.canUseFullScreenIntent(context))
    }

    @Test
    fun settingsOpenTheExactAlarmScreenOfTheApp() {
        val intent = ExactAlarmPermission.settingsIntent(context)

        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    @Config(sdk = [30])
    fun settingsOpenTheAppDetailsBeforeAndroid12() {
        val intent = ExactAlarmPermission.settingsIntent(context)

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }
}

/**
 * Robolectric 4.16.1 não tem setter para a tela cheia: o valor vem do INotificationManager do sistema, que responde
 * "não". Esta função registra um serviço falso que responde [granted] no lugar dele. É um hack de teste, usado só porque
 * a versão não oferece alternativa; quando o Robolectric tiver um setter, troque por ele e apague estes helpers.
 */
internal fun grantFullScreenIntent(granted: Boolean) {
    clearCachedNotificationService()
    val type = Class.forName("android.app.INotificationManager")
    lateinit var binder: Binder
    val service = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        when (method.name) {
            "asBinder" -> binder
            "canUseFullScreenIntent" -> granted
            else -> null
        }
    } as IInterface
    binder = Binder().apply { attachInterface(service, type.name) }
    @Suppress("UNCHECKED_CAST")
    ShadowServiceManager.addBinderService(Context.NOTIFICATION_SERVICE, type as Class<out IInterface>, service)
}

/** Devolve ao NotificationManager o serviço do sistema, para o próximo teste; desfaz [grantFullScreenIntent]. */
internal fun restoreNotificationService() {
    clearCachedNotificationService()
    ShadowServiceManager.reset()
}

/** O NotificationManager guarda o serviço num campo estático; zerá-lo faz o próximo acesso buscar o registrado. */
private fun clearCachedNotificationService() {
    NotificationManager::class.java.getDeclaredField("sService").apply { isAccessible = true }.set(null, null)
}
