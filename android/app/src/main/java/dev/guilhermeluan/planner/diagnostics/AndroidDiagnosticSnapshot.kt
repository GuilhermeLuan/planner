package dev.guilhermeluan.planner.diagnostics

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dev.guilhermeluan.planner.notifications.PlannerNotificationScheduler
import org.json.JSONObject
import java.time.Clock
import java.time.ZoneId

class AndroidDiagnosticSnapshot(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : DiagnosticSnapshotProvider {
    override fun snapshot(accountTimezone: String): JSONObject {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val channel = notifications.getNotificationChannel(PlannerNotificationScheduler.CHANNEL_ID)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return JSONObject()
            .put("version", 1)
            .put("generatedAt", clock.instant().toString())
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("androidVersion", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("appVersion", packageInfo.versionName)
            .put("appVersionCode", packageInfo.longVersionCode)
            .put("deviceTimezone", ZoneId.systemDefault().id)
            .put("accountTimezone", accountTimezone)
            .put("postNotificationsGranted", ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            .put("notificationsEnabled", notifications.areNotificationsEnabled())
            .put("channelExists", channel != null)
            .put("channelImportance", channel?.importance ?: JSONObject.NULL)
            .put("canScheduleExactAlarms", Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms())
            .put("ignoringBatteryOptimizations", power.isIgnoringBatteryOptimizations(context.packageName))
            .put("doNotDisturbFilter", notifications.currentInterruptionFilter)
            .put("samsungDeepSleep", "unavailable")
    }
}
