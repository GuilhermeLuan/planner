package dev.guilhermeluan.planner.alarm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.notifications.RingingDoseAlarm
import dev.guilhermeluan.planner.notifications.SNOOZE_LABEL
import dev.guilhermeluan.planner.notifications.reason
import dev.guilhermeluan.planner.notifications.title
import dev.guilhermeluan.planner.ui.components.ClockFormatter
import dev.guilhermeluan.planner.ui.components.PtBr
import dev.guilhermeluan.planner.ui.theme.alarmTones
import java.time.format.DateTimeFormatter

/** O que a tela de alarme mostra (tela "07 · Alarme tocando" do Figma). */
data class DoseAlarmUiState(
    val dateText: String,
    val timeText: String,
    /** "Hora do Magnésio", como na notificação. */
    val title: String,
    /** "2 comprimidos · lembrete às 21:30 ainda sem registro", como na notificação. */
    val reason: String,
) {
    companion object {
        private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PtBr)

        fun of(alarm: RingingDoseAlarm) = DoseAlarmUiState(
            dateText = alarm.ringAt.format(DateFormatter).replaceFirstChar(Char::uppercase),
            timeText = alarm.ringAt.format(ClockFormatter),
            title = alarm.title,
            reason = alarm.reason,
        )
    }
}

private val Stars = listOf(
    30f to 60f, 90f to 120f, 300f to 90f, 250f to 40f, 180f to 150f, 330f to 200f, 40f to 230f, 140f to 70f,
)

/** Alarme em tela cheia: hora, Remédio, dose e as ações Tomei, Adiar e Pular dose. */
@Composable
fun DoseAlarmScreen(
    state: DoseAlarmUiState,
    onTake: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tones = alarmTones
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tones.skyTop, tones.skyBottom)))) {
        AlarmBackdrop(tones.hills)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(46.dp))
            Text(state.dateText, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = tones.softInk)
            Text(
                state.timeText,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 72.sp, lineHeight = 84.sp, letterSpacing = (-1).sp),
                color = tones.ink,
            )
            Spacer(Modifier.height(54.dp))
            Pulse(disc = tones.button, pill = tones.buttonInk)
            Spacer(Modifier.height(40.dp))
            Text(state.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), color = tones.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                state.reason,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = tones.softInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Tomei",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                    color = tones.buttonInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(alpha = 0.18f))
                        .clip(RoundedCornerShape(32.dp))
                        .background(tones.button)
                        .clickable(role = Role.Button, onClick = onTake)
                        .padding(vertical = 20.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostAction(SNOOZE_LABEL, tones.ink, onSnooze, Modifier.weight(1f))
                    GhostAction("Pular dose", tones.ink, onSkip, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GhostAction(text: String, ink: Color, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

@Composable
private fun AlarmBackdrop(hills: List<Color>) {
    Canvas(Modifier.fillMaxSize()) {
        Stars.forEachIndexed { index, (x, y) ->
            drawCircle(Color.White.copy(alpha = 0.7f), radius = (if (index % 3 == 0) 1.5f else 1f).dp.toPx(), center = Offset(x.dp.toPx(), y.dp.toPx()))
        }
        val bottom = size.height
        drawOval(hills[0], Offset(-120.dp.toPx(), bottom - 240.dp.toPx()), Size(420.dp.toPx(), 320.dp.toPx()))
        drawOval(hills[1], Offset(130.dp.toPx(), bottom - 210.dp.toPx()), Size(420.dp.toPx(), 300.dp.toPx()))
        drawOval(hills[2], Offset(-60.dp.toPx(), bottom - 150.dp.toPx()), Size(520.dp.toPx(), 260.dp.toPx()))
    }
}

/** Três anéis em volta do disco com a pílula; o pulso é a única animação da tela. */
@Composable
private fun Pulse(disc: Color, pill: Color) {
    val scale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse-scale",
    )
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            listOf(200f to 0.08f, 156f to 0.12f, 114f to 0.18f).forEach { (diameter, alpha) ->
                drawCircle(Color.White.copy(alpha = alpha), radius = diameter.dp.toPx() / 2 * scale)
            }
            drawCircle(disc, radius = 40.dp.toPx())
            rotate(-45f) {
                drawRoundRect(
                    pill,
                    topLeft = Offset(center.x - 11.dp.toPx(), center.y - 4.5.dp.toPx()),
                    size = Size(22.dp.toPx(), 9.dp.toPx()),
                    cornerRadius = CornerRadius(4.5.dp.toPx()),
                )
            }
        }
    }
}
