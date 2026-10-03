package dev.guilhermeluan.planner.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.day.StartDatePickerDialog
import dev.guilhermeluan.planner.day.TimePickerDialog
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import dev.guilhermeluan.planner.tasks.PlannerMedicine
import dev.guilhermeluan.planner.tasks.editEffectiveFrom
import dev.guilhermeluan.planner.ui.components.FormField
import dev.guilhermeluan.planner.ui.components.PlannerChip
import dev.guilhermeluan.planner.ui.components.PlannerFormSheet
import dev.guilhermeluan.planner.ui.components.PtBr
import dev.guilhermeluan.planner.ui.components.formFieldColors
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val FirstDoseTime = LocalTime.of(8, 0)
private val ExtraDoseTimeSuggestion = LocalTime.of(9, 0)

/**
 * Formulário "Novo remédio" (tela 05 do Figma), sem alarme de dose. Com [medicine] vira "Editar remédio",
 * começando dos valores atuais; [onArchive] acrescenta a ação "Arquivar remédio" e [editNotice] avisa
 * quando a edição só vale mais adiante.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MedicineForm(
    initialDay: LocalDate,
    onSave: (MedicineDraft) -> Unit,
    modifier: Modifier = Modifier,
    medicine: PlannerMedicine? = null,
    onArchive: (() -> Unit)? = null,
    editNotice: String? = null,
) {
    val currentRepeat = medicine?.repeat
    var name by rememberSaveable { mutableStateOf(medicine?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(medicine?.amount ?: 1) }
    var unit by rememberSaveable { mutableStateOf(medicine?.unit ?: DoseUnit.TABLET) }
    var times by rememberSaveable {
        mutableStateOf(medicine?.times?.sorted()?.map(LocalTime::toString) ?: listOf(FirstDoseTime.toString()))
    }
    var stock by rememberSaveable { mutableStateOf(medicine?.stock?.amount?.toString().orEmpty()) }
    // O Estoque pode mudar com o formulário aberto (um "Tomei" na notificação); o que vale é o da abertura.
    val stockAsShown by rememberSaveable { mutableStateOf(medicine?.stock?.amount) }
    var stockThreshold by rememberSaveable { mutableStateOf(medicine?.stock?.threshold?.toString().orEmpty()) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var repeatKind by rememberSaveable {
        mutableStateOf(
            when (currentRepeat) {
                is MedicineRepeat.Weekdays -> RepeatKind.WEEKDAYS
                is MedicineRepeat.Period -> RepeatKind.PERIOD
                else -> RepeatKind.DAILY
            },
        )
    }
    var weekdays by rememberSaveable {
        mutableStateOf(
            (currentRepeat as? MedicineRepeat.Weekdays)?.days?.map(DayOfWeek::getValue)?.toSet()
                ?: setOf(initialDay.dayOfWeek.value),
        )
    }
    var periodStart by rememberSaveable {
        mutableStateOf(((currentRepeat as? MedicineRepeat.Period)?.start ?: initialDay).toString())
    }
    var periodEnd by rememberSaveable {
        mutableStateOf(((currentRepeat as? MedicineRepeat.Period)?.end ?: initialDay.plusDays(6)).toString())
    }
    var pickingDate by rememberSaveable { mutableStateOf<PeriodEdge?>(null) }
    val palette = PlannerExtras.palette
    val repeat = when (repeatKind) {
        RepeatKind.DAILY -> MedicineRepeat.Daily
        RepeatKind.WEEKDAYS -> MedicineRepeat.Weekdays(weekdays.map(DayOfWeek::of).toSet())
        RepeatKind.PERIOD -> MedicineRepeat.Period(LocalDate.parse(periodStart), LocalDate.parse(periodEnd))
    }
    val repeatValid = when (repeat) {
        MedicineRepeat.Daily -> true
        is MedicineRepeat.Weekdays -> repeat.days.isNotEmpty()
        is MedicineRepeat.Period -> !repeat.end.isBefore(repeat.start)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(if (medicine == null) "Novo remédio" else "Editar remédio", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))
        editNotice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryInk)
        }

        FormField("Nome") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().testTag("medicine-name"),
                placeholder = { Text("Ex.: Vitamina D") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = formFieldColors(),
            )
        }

        FormField("Dose") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepperGlyph("−", "Diminuir quantidade") { if (amount > 1) amount-- }
                    Text("$amount", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                    StepperGlyph("+", "Aumentar quantidade") { amount++ }
                }
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DoseUnit.entries.forEach { option ->
                        PlannerChip(option.label, selected = option == unit, onClick = { unit = option })
                    }
                }
            }
        }

        FormField("Horários") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                times.sorted().forEach { time ->
                    PlannerChip(time, selected = true, onClick = { if (times.size > 1) times = times - time })
                }
                PlannerChip("+ Adicionar horário", selected = false, onClick = { pickingTime = true })
            }
        }

        FormField("Repetir") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatKind.entries.forEach { kind ->
                    PlannerChip(kind.label, selected = kind == repeatKind, onClick = { repeatKind = kind })
                }
            }
        }

        when (repeatKind) {
            RepeatKind.DAILY -> Unit
            RepeatKind.WEEKDAYS -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayOfWeek.entries.forEach { weekday ->
                    PlannerChip(
                        text = weekday.getDisplayName(TextStyle.SHORT, PtBr).removeSuffix(".").replaceFirstChar(Char::uppercase),
                        selected = weekday.value in weekdays,
                        onClick = {
                            weekdays = if (weekday.value in weekdays) weekdays - weekday.value else weekdays + weekday.value
                        },
                    )
                }
            }
            RepeatKind.PERIOD -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Começa em", periodStart, "medicine-period-start", Modifier.weight(1f)) { pickingDate = PeriodEdge.START }
                DateField("Termina em", periodEnd, "medicine-period-end", Modifier.weight(1f)) { pickingDate = PeriodEdge.END }
            }
        }

        FormField("Estoque") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StockField(
                    value = stock,
                    onChange = { stock = it },
                    placeholder = "Quantidade",
                    suffix = unit.noun(stock.toIntOrNull() ?: 0),
                    tag = "medicine-stock",
                    modifier = Modifier.weight(1f),
                )
                StockField(
                    value = stockThreshold,
                    onChange = { stockThreshold = it },
                    placeholder = "Avisar com",
                    suffix = null,
                    tag = "medicine-stock-threshold",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Button(
            enabled = name.trim().isNotEmpty() && repeatValid,
            onClick = {
                val stockAmount = stock.toIntOrNull()
                onSave(
                    MedicineDraft(
                        name.trim(), amount, unit, times.map(LocalTime::parse).toSet(), repeat, initialDay,
                        stock = stockAmount,
                        stockThreshold = stockThreshold.toIntOrNull(),
                        stockAsShown = stockAsShown,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Salvar remédio", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
        }

        if (onArchive != null) {
            TextButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Arquivar remédio",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                    color = palette.alertAmber,
                )
            }
        }
    }

    if (pickingTime) {
        TimePickerDialog(
            initial = ExtraDoseTimeSuggestion,
            onDismiss = { pickingTime = false },
            onConfirm = {
                times = (times + it.toString()).distinct()
                pickingTime = false
            },
        )
    }
    pickingDate?.let { edge ->
        StartDatePickerDialog(
            initial = LocalDate.parse(if (edge == PeriodEdge.START) periodStart else periodEnd),
            onDismiss = { pickingDate = null },
            onConfirm = {
                if (edge == PeriodEdge.START) periodStart = it.toString() else periodEnd = it.toString()
                pickingDate = null
            },
        )
    }
}

@Composable
private fun StockField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    suffix: String?,
    tag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter(Char::isDigit).take(5)) },
        modifier = modifier.testTag(tag),
        placeholder = { Text(placeholder, maxLines = 1) },
        suffix = suffix?.takeIf { value.isNotEmpty() }?.let { { Text(it, maxLines = 1) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        colors = formFieldColors(),
    )
}

private enum class PeriodEdge { START, END }

private enum class RepeatKind(val label: String) {
    DAILY("Todo dia"),
    WEEKDAYS("Dias da semana"),
    PERIOD("Por um período"),
}

@Composable
private fun DateField(label: String, isoDate: String, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val palette = PlannerExtras.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = palette.secondaryInk)
        Text(
            LocalDate.parse(isoDate).format(ShortDateFormatter),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .border(1.dp, palette.line, RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

private val ShortDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", PtBr)

@Composable
private fun StepperGlyph(glyph: String, description: String, onClick: () -> Unit) {
    Text(
        glyph,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
    )
}

/** Folha inferior "Editar remédio", com a ação de arquivar. */
@Composable
fun EditMedicineSheet(
    medicine: PlannerMedicine,
    initialDay: LocalDate,
    today: LocalDate,
    lastRegisteredDay: LocalDate?,
    onDismiss: () -> Unit,
    onSave: (MedicineDraft) -> Unit,
    onArchive: () -> Unit,
) {
    PlannerFormSheet(onDismiss) {
        MedicineForm(
            initialDay = initialDay,
            onSave = onSave,
            medicine = medicine,
            onArchive = onArchive,
            editNotice = editNotice(today, lastRegisteredDay),
        )
    }
}

private val NoticeDayFormatter = DateTimeFormatter.ofPattern("dd/MM", PtBr)

/** Aviso de que a edição começa depois de hoje, porque já há Dose registrada; nulo quando vale hoje. */
private fun editNotice(today: LocalDate, lastRegisteredDay: LocalDate?): String? {
    val from = editEffectiveFrom(today, lastRegisteredDay)
    return when {
        from == today -> null
        from == today.plusDays(1) -> "Já há dose registrada hoje, então as mudanças valem a partir de amanhã."
        else -> "Já há dose registrada até ${lastRegisteredDay?.format(NoticeDayFormatter)}, " +
            "então as mudanças valem a partir de ${from.format(NoticeDayFormatter)}."
    }
}

/** Folha inferior "Novo remédio". */
@Composable
fun NewMedicineSheet(
    initialDay: LocalDate,
    onDismiss: () -> Unit,
    onSave: (MedicineDraft) -> Unit,
) {
    PlannerFormSheet(onDismiss) { MedicineForm(initialDay = initialDay, onSave = onSave) }
}
