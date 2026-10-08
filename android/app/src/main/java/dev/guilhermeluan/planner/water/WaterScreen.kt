package dev.guilhermeluan.planner.water

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.ui.components.ClockFormatter
import dev.guilhermeluan.planner.ui.components.PlannerScene
import dev.guilhermeluan.planner.ui.components.PtBr
import dev.guilhermeluan.planner.ui.components.SceneColors
import dev.guilhermeluan.planner.ui.navigation.PlannerTab
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import java.time.LocalDate
import java.time.format.TextStyle

/** O dia de hoje e a semana dele; a aba Água não registra em outros Dias. */
data class WaterUiState(
    val day: WaterDay,
    val week: List<WaterDay> = emptyList(),
    /** O Dia selecionado na aba Hoje (hoje, até outro ser escolhido); só alimenta o resumo "Seu dia". */
    val viewedDay: WaterDay = day,
    /** Lembrete de água da Conta; desligado até ser salvo. */
    val reminder: WaterReminderSettings = WaterReminderSettings(),
)

private val HeaderTitleInk = Color(0xFFFFF4F8)
private val HeaderSubtitleInk = Color(0xFFEFDDF5)
private val WaveBack = Color(0xFFB9C7F2)
private val WaveFrontTop = Color(0xFF8C9EE6)
private val WaveFrontBottom = Color(0xFF6474C2)
private val CupShadow = Color(0x2E5868B0)
private val AddButtonPrimaryCaption = Color(0xFFDDE3FF)

/** Tons de lavanda da aba Água; os claros são os do Figma, os escuros seguem os tokens noturnos. */
private data class WaterTones(
    val cupWater: Color,
    val cardLine: Color,
    val buttonLine: Color,
    val buttonInk: Color,
    val buttonCaption: Color,
) {
    companion object {
        val Light = WaterTones(
            cupWater = Color(0xFFEEF0FC),
            cardLine = Color(0xFFF6DCE6),
            buttonLine = Color(0xFFDCE1F6),
            buttonInk = Color(0xFF3D4C93),
            buttonCaption = Color(0xFF8A92B8),
        )
        val Dark = WaterTones(
            cupWater = Color(0xFF2B2740),
            cardLine = Color(0xFF5F3D4B),
            buttonLine = Color(0xFF3B3A5A),
            buttonInk = Color(0xFFC9D0F5),
            buttonCaption = Color(0xFF9EA5C8),
        )
    }
}

private val waterTones: WaterTones
    @Composable @ReadOnlyComposable get() =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) WaterTones.Dark else WaterTones.Light

/** Aba Água (tela 03 do Figma): copo com o Consumo do Dia, botões de somar e a semana. */
@Composable
fun WaterScreen(
    state: WaterUiState,
    onAdd: (ml: Int) -> Unit,
    onAdjustTotal: (totalMl: Int) -> Unit,
    onSetGoal: (goalMl: Int) -> Unit,
    onSaveReminder: (settings: WaterReminderSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = state.day
    var choosingAmount by rememberSaveable { mutableStateOf(false) }
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    var editingReminder by rememberSaveable { mutableStateOf(false) }
    Surface(modifier.fillMaxSize().testTag("water-screen"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            WaterHeader("Meta de ${WaterText.liters(day.goalMl)} por dia") { editingGoal = true }
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                WaterCup(day.consumedMl, day.goalMl)
                Text(
                    WaterText.remaining(day.consumedMl, day.goalMl),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PlannerExtras.palette.secondaryInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(260.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AddButton("+ ${WaterText.GLASS_ML} ml", "Copo", primary = true, Modifier.weight(1f)) { onAdd(WaterText.GLASS_ML) }
                    AddButton("+ ${WaterText.BOTTLE_ML} ml", "Garrafa", primary = false, Modifier.weight(1f)) { onAdd(WaterText.BOTTLE_ML) }
                    AddButton("Outro valor", "ajustar total", primary = false, Modifier.weight(1f)) { choosingAmount = true }
                }
                ReminderCard(state.reminder) { editingReminder = true }
                if (state.week.isNotEmpty()) WeekCard(state.week, today = day.day)
            }
        }
    }

    if (editingGoal) {
        WaterGoalSheet(
            currentGoalMl = day.goalMl,
            onDismiss = { editingGoal = false },
            onSave = {
                editingGoal = false
                onSetGoal(it)
            },
        )
    }
    if (choosingAmount) {
        WaterAmountSheet(
            currentTotalMl = day.consumedMl,
            onDismiss = { choosingAmount = false },
            onAdd = {
                choosingAmount = false
                onAdd(it)
            },
            onAdjustTotal = {
                choosingAmount = false
                onAdjustTotal(it)
            },
        )
    }
    if (editingReminder) {
        WaterReminderSheet(
            current = state.reminder,
            onDismiss = { editingReminder = false },
            onSave = {
                editingReminder = false
                onSaveReminder(it)
            },
        )
    }
}

@Composable
private fun WaterHeader(subtitle: String, onEditGoal: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(208.dp).clipToBounds()) {
        PlannerScene(SceneColors.forTab(PlannerTab.Water), height = 230.dp)
        Column(Modifier.padding(start = 24.dp, top = 56.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Água", style = MaterialTheme.typography.displaySmall, color = HeaderTitleInk)
            Row(
                Modifier.clickable(onClickLabel = "Alterar meta de água", role = Role.Button, onClick = onEditGoal),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = HeaderSubtitleInk)
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = HeaderSubtitleInk, modifier = Modifier.size(14.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun AddButton(label: String, caption: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = PlannerExtras.palette
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (primary) palette.waterLavender else palette.surface)
            .then(if (primary) Modifier else Modifier.border(1.dp, waterTones.buttonLine, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
            color = if (primary) Color.White else waterTones.buttonInk,
            maxLines = 1,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = if (primary) AddButtonPrimaryCaption else waterTones.buttonCaption,
            maxLines = 1,
        )
    }
}

/** Cartão do Lembrete de água: diz se está desligado ou o intervalo e a janela; o toque abre a folha. */
@Composable
private fun ReminderCard(reminder: WaterReminderSettings, onClick: () -> Unit) {
    val palette = PlannerExtras.palette
    val shape = RoundedCornerShape(22.dp)
    val summary = if (reminder.enabled) {
        "A cada ${reminder.intervalHours} h · ${reminder.windowStart.format(ClockFormatter)}–${reminder.windowEnd.format(ClockFormatter)}"
    } else {
        "Desligado"
    }
    Column(
        Modifier
            .testTag("water-reminder-card")
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, waterTones.cardLine, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Lembrar de beber água", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp), color = palette.wine)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryInk)
    }
}

@Composable
private fun WeekCard(week: List<WaterDay>, today: LocalDate) {
    val palette = PlannerExtras.palette
    val metDays = week.count(WaterDay::goalMet)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .border(1.dp, waterTones.cardLine, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Esta semana", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp), color = palette.wine)
            Text(
                "Meta batida em $metDays ${if (metDays == 1) "dia" else "dias"}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.waterLavender,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            week.forEach { day -> WeekBar(day, isToday = day.day == today) }
        }
    }
}

@Composable
private fun WeekBar(day: WaterDay, isToday: Boolean) {
    val palette = PlannerExtras.palette
    val progress = day.progress
    val fill = when {
        day.goalMet -> palette.waterLavender
        isToday -> WaveFrontTop
        else -> WaveBack
    }
    val label = day.day.dayOfWeek.getDisplayName(TextStyle.NARROW, PtBr).uppercase(PtBr)
    Column(
        Modifier
            .testTag("water-bar-${day.day}")
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                contentDescription = "${day.day.dayOfWeek.getDisplayName(TextStyle.FULL, PtBr)}: " +
                    "${WaterText.ml(day.consumedMl)} de ${WaterText.ml(day.goalMl)} ml"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.width(22.dp).height(84.dp).clip(RoundedCornerShape(11.dp)).background(waterTones.cupWater),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (progress > 0f) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(progress).clip(RoundedCornerShape(11.dp)).background(fill))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = if (isToday) palette.wine else palette.mutedInk,
        )
    }
}

/** Copo redondo que enche em onda conforme o Consumo se aproxima da meta. */
@Composable
fun WaterCup(consumedMl: Int, goalMl: Int, modifier: Modifier = Modifier) {
    val progress = waterProgress(consumedMl, goalMl)
    val level by animateFloatAsState(progress, tween(durationMillis = 700), label = "nível do copo")
    // O texto de baixo fica branco quando a água já o cobre, como no Figma.
    val labelUnderWater = level >= 0.47f
    Box(
        modifier
            .size(220.dp)
            .shadow(30.dp, CircleShape, ambientColor = CupShadow, spotColor = CupShadow)
            .clip(CircleShape)
            .background(PlannerExtras.palette.surface)
            .padding(8.dp)
            .clip(CircleShape)
            .background(waterTones.cupWater)
            .testTag("water-cup")
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = 1.dp.toPx()
            val frontTop = size.height * (1 - level) - 2 * unit
            drawPath(backWave(frontTop - 10 * unit, unit, size.height), WaveBack)
            drawPath(
                frontWave(frontTop, unit, size.height),
                Brush.verticalGradient(listOf(WaveFrontTop, WaveFrontBottom), startY = frontTop, endY = size.height),
            )
        }
        Column(Modifier.padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                WaterText.ml(consumedMl),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 46.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "de ${WaterText.ml(goalMl)} ml",
                style = MaterialTheme.typography.labelLarge,
                color = if (labelUnderWater) Color.White else PlannerExtras.palette.waterLavender,
            )
        }
    }
}

// As ondas seguem os vetores do Figma (220dp de largura, começando 8dp antes da borda interna).
private fun backWave(top: Float, unit: Float, bottom: Float) = wave(top, unit, bottom) {
    moveTo(0f, 6.85f)
    cubicTo(40f, -7.15f, 80f, 18.85f, 120f, 4.85f)
    cubicTo(160f, -9.15f, 195f, 12.85f, 220f, 0.85f)
}

private fun frontWave(top: Float, unit: Float, bottom: Float) = wave(top, unit, bottom) {
    moveTo(0f, 5.07f)
    cubicTo(50f, 19.07f, 90f, -8.93f, 140f, 3.07f)
    cubicTo(180f, 13.07f, 205f, -0.93f, 220f, 3.07f)
}

private fun wave(top: Float, unit: Float, bottom: Float, crest: Path.() -> Unit) = Path().apply {
    crest()
    lineTo(220f, (bottom - top) / unit)
    lineTo(0f, (bottom - top) / unit)
    close()
    transform(androidx.compose.ui.graphics.Matrix().apply { scale(unit, unit) })
    translate(Offset(-8 * unit, top))
}
