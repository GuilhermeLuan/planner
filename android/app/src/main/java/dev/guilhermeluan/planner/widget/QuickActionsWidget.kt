package dev.guilhermeluan.planner.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.guilhermeluan.planner.MainActivity
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.R
import dev.guilhermeluan.planner.notifications.ExactAlarmPermission
import dev.guilhermeluan.planner.notifications.runAsync
import dev.guilhermeluan.planner.ui.theme.PlannerPalette
import java.time.Instant

private val PaletteLight = PlannerPalette.Light
private val PaletteDark = PlannerPalette.Dark

private fun tone(light: (PlannerPalette) -> Color, dark: (PlannerPalette) -> Color = light) =
    ColorProvider(day = light(PaletteLight), night = dark(PaletteDark))

private val Porcelain = tone({ it.porcelain })
private val Blush = tone({ it.blush })
private val Raspberry = tone({ it.raspberry })
private val OnRaspberry = tone({ it.onRaspberry })
private val Wine = tone({ it.wine })

/** Widget largo (4×1) com os atalhos Tarefa, Água e Remédio; Água e Remédio confirmam o toque por ~3 s. */
class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = (context.applicationContext as PlannerApplication).quickConfirmations
        provideContent {
            val now = Instant.now()
            QuickActionsContent(
                context = context,
                water = store.active(QuickKind.Water, now),
                medicine = store.active(QuickKind.Medicine, now),
            )
        }
    }

    companion object {
        const val EXTRA_KIND = "quickKind"

        /** Redesenha o widget e agenda o retorno ao repouso quando [confirmation] expirar. */
        suspend fun refresh(context: Context, confirmation: QuickConfirmation? = null) {
            QuickActionsWidget().updateAll(context)
            confirmation?.let { scheduleReset(context, it.until) }
        }

        private fun resetIntent(context: Context) =
            Intent(context, QuickActionsResetReceiver::class.java).setData(Uri.parse("planner://widget/quick-reset"))

        private fun scheduleReset(context: Context, at: Instant) {
            val pending = PendingIntent.getBroadcast(
                context, 0, resetIntent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // Exato quando a permissão existe, para os ~3 s valerem; sem ela, o sistema pode atrasar o retorno.
            // A releitura da confirmação expirada em provideGlance garante o repouso em qualquer redesenho.
            val alarms = context.getSystemService(AlarmManager::class.java)
            if (ExactAlarmPermission.canScheduleExactAlarms(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, at.toEpochMilli(), pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC, at.toEpochMilli(), pending)
            }
        }
    }
}

@Composable
private fun QuickActionsContent(context: Context, water: QuickConfirmation?, medicine: QuickConfirmation?) {
    val newTask = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_NEW_TASK),
    )
    Row(
        modifier = GlanceModifier.fillMaxSize().background(Porcelain).cornerRadius(28.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickButton(
            modifier = GlanceModifier.defaultWeight(),
            icon = R.drawable.ic_widget_add_task, label = "Tarefa", description = "Nova tarefa",
            confirmation = null, onClick = newTask,
        )
        Spacer(GlanceModifier.width(8.dp))
        QuickButton(
            modifier = GlanceModifier.defaultWeight(),
            icon = R.drawable.ic_widget_water_drop, label = "Água", description = "Beber um copo de água",
            confirmation = water, onClick = trampoline(context, QuickKind.Water),
        )
        Spacer(GlanceModifier.width(8.dp))
        QuickButton(
            modifier = GlanceModifier.defaultWeight(),
            icon = R.drawable.ic_widget_pill, label = "Remédio", description = "Marcar o remédio como tomado",
            confirmation = medicine, onClick = trampoline(context, QuickKind.Medicine),
        )
    }
}

private fun trampoline(context: Context, kind: QuickKind): Action =
    actionStartActivity(Intent(context, QuickActionActivity::class.java).putExtra(QuickActionsWidget.EXTRA_KIND, kind.name))

@Composable
private fun QuickButton(
    modifier: GlanceModifier,
    icon: Int,
    label: String,
    description: String,
    confirmation: QuickConfirmation?,
    onClick: Action,
) {
    val confirmed = confirmation != null
    val ink = if (confirmed) OnRaspberry else Raspberry
    Box(
        modifier = modifier.height(64.dp)
            .background(if (confirmed) Raspberry else Blush)
            .cornerRadius(20.dp)
            .semantics { contentDescription = confirmation?.description ?: description }
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            if (confirmation == null) {
                Image(ImageProvider(icon), contentDescription = null, colorFilter = ColorFilter.tint(ink), modifier = GlanceModifier.size(24.dp))
                Text(
                    label,
                    style = TextStyle(color = Wine, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                )
            } else {
                Image(ImageProvider(R.drawable.ic_widget_check), contentDescription = null, colorFilter = ColorFilter.tint(ink), modifier = GlanceModifier.size(18.dp))
                Text(
                    confirmation.title,
                    style = TextStyle(color = OnRaspberry, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
                Text(
                    confirmation.detail,
                    style = TextStyle(color = ColorProvider(day = PaletteLight.onRaspberry.copy(alpha = 0.85f), night = PaletteDark.onRaspberry.copy(alpha = 0.85f)), fontSize = 10.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
        }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

/** Devolve os botões ao repouso quando a confirmação expira, com o app fechado. */
class QuickActionsResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync { QuickActionsWidget.refresh(context) }
    }
}
