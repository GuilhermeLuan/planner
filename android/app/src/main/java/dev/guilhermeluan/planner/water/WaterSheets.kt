package dev.guilhermeluan.planner.water

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.day.TimePickerDialog
import dev.guilhermeluan.planner.ui.components.ClockFormatter
import dev.guilhermeluan.planner.ui.components.FormField
import dev.guilhermeluan.planner.ui.components.PlannerChip
import dev.guilhermeluan.planner.ui.components.PlannerFormSheet
import dev.guilhermeluan.planner.ui.components.PlannerPrimaryButton
import dev.guilhermeluan.planner.ui.components.PlannerSwitch
import dev.guilhermeluan.planner.ui.components.formFieldColors
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import java.time.LocalTime

private enum class AmountMode(val label: String) { ADD("Somar"), ADJUST("Ajustar total") }

/** Folha "Outro valor": soma uma quantidade qualquer ou corrige o total do Dia. */
@Composable
fun WaterAmountSheet(
    currentTotalMl: Int,
    onDismiss: () -> Unit,
    onAdd: (ml: Int) -> Unit,
    onAdjustTotal: (totalMl: Int) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(AmountMode.ADD) }
    var amount by rememberSaveable { mutableStateOf("") }
    val value = amount.toIntOrNull()
    PlannerFormSheet(onDismiss) {
        SheetColumn {
            Text("Outro valor", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountMode.entries.forEach { option ->
                    PlannerChip(option.label, selected = option == mode, onClick = {
                        mode = option
                        amount = if (option == AmountMode.ADJUST) currentTotalMl.toString() else ""
                    })
                }
            }
            FormField(if (mode == AmountMode.ADD) "Quanto você bebeu" else "Total do dia") {
                MlField(amount, onChange = { amount = it }, placeholder = "Ex.: 350", tag = "water-amount")
            }
            when (mode) {
                AmountMode.ADD -> PlannerPrimaryButton("Somar água", onClick = { value?.let(onAdd) }, enabled = value != null && value > 0)
                AmountMode.ADJUST -> PlannerPrimaryButton("Salvar total", onClick = { value?.let(onAdjustTotal) }, enabled = value != null)
            }
        }
    }
}

private val GoalSuggestionsMl = listOf(1500, 2000, 2500, 3000)

/** Folha "Meta de água": a nova meta vale de hoje em diante. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun WaterGoalSheet(
    currentGoalMl: Int,
    onDismiss: () -> Unit,
    onSave: (goalMl: Int) -> Unit,
) {
    var goal by rememberSaveable { mutableStateOf(currentGoalMl.toString()) }
    val value = goal.toIntOrNull()
    PlannerFormSheet(onDismiss) {
        SheetColumn {
            Text("Meta de água", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))
            Text(
                "Vale a partir de hoje. Os dias anteriores mantêm a meta que tinham.",
                style = MaterialTheme.typography.bodyMedium,
                color = PlannerExtras.palette.secondaryInk,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalSuggestionsMl.forEach { ml ->
                    PlannerChip(WaterText.shortLiters(ml), selected = value == ml, onClick = { goal = ml.toString() })
                }
            }
            FormField("Meta por dia") {
                MlField(goal, onChange = { goal = it }, placeholder = "Ex.: 2000", tag = "water-goal")
            }
            PlannerPrimaryButton("Salvar meta", onClick = { value?.let(onSave) }, enabled = value != null && value > 0)
        }
    }
}

/** Folha "Lembrete de água": liga o Lembrete de água e escolhe o intervalo e a janela do Dia em que ele se repete. */
@Composable
fun WaterReminderSheet(
    current: WaterReminderSettings,
    onDismiss: () -> Unit,
    onSave: (WaterReminderSettings) -> Unit,
) {
    var enabled by rememberSaveable { mutableStateOf(current.enabled) }
    var intervalHours by rememberSaveable { mutableStateOf(current.intervalHours) }
    var windowStart by rememberSaveable { mutableStateOf(current.windowStart.toString()) }
    var windowEnd by rememberSaveable { mutableStateOf(current.windowEnd.toString()) }
    val start = LocalTime.parse(windowStart)
    val end = LocalTime.parse(windowEnd)
    val settings = WaterReminderSettings(enabled = enabled, intervalHours = intervalHours, windowStart = start, windowEnd = end)
    PlannerFormSheet(onDismiss) {
        SheetColumn {
            Text("Lembrete de água", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Lembrar de beber água", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                PlannerSwitch(checked = enabled, onCheckedChange = { enabled = it }, modifier = Modifier.testTag("water-reminder-switch"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaterReminderSettings.INTERVAL_HOURS.forEach { hours ->
                    PlannerChip("$hours h", selected = hours == intervalHours, onClick = { intervalHours = hours })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WaterTimeField("Das", start, onChange = { windowStart = it.toString() }, tag = "water-reminder-start", modifier = Modifier.weight(1f))
                WaterTimeField("Até", end, onChange = { windowEnd = it.toString() }, tag = "water-reminder-end", modifier = Modifier.weight(1f))
            }
            PlannerPrimaryButton("Salvar lembrete", onClick = { onSave(settings) }, enabled = settings.isValid)
        }
    }
}

/** Campo de horário da janela do Lembrete: o toque abre o diálogo de hora e o valor escolhido aparece no campo. */
@Composable
private fun WaterTimeField(
    label: String,
    value: LocalTime,
    onChange: (LocalTime) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { isOpen = true },
        modifier = modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PlannerExtras.palette.line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = PlannerExtras.palette.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(imageVector = Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = value.format(ClockFormatter),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (isOpen) {
        TimePickerDialog(
            initial = value,
            onDismiss = { isOpen = false },
            onConfirm = {
                onChange(it)
                isOpen = false
            },
        )
    }
}

@Composable
private fun SheetColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) { content() }
}

@Composable
private fun MlField(value: String, onChange: (String) -> Unit, placeholder: String, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter(Char::isDigit).take(5)) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        placeholder = { Text(placeholder, color = PlannerExtras.palette.mutedInk) },
        suffix = { Text("ml") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        colors = formFieldColors(),
    )
}
