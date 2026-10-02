package dev.guilhermeluan.planner.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.tasks.RoutineDraft
import dev.guilhermeluan.planner.ui.components.FormField
import dev.guilhermeluan.planner.ui.components.PlannerChip
import dev.guilhermeluan.planner.ui.components.PtBr
import dev.guilhermeluan.planner.ui.components.formFieldColors
import dev.guilhermeluan.planner.ui.components.PlannerFormSheet
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal val StartDateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PtBr)

/** Folha inferior "Nova rotina", no mesmo desenho da folha de novo remédio do Figma. */
@Composable
fun NewRoutineSheet(
    initialDay: LocalDate,
    onDismiss: () -> Unit,
    onSave: (RoutineDraft) -> Unit,
) {
    PlannerFormSheet(onDismiss) { RoutineForm(initialDay = initialDay, onSave = onSave) }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RoutineForm(
    initialDay: LocalDate,
    onSave: (RoutineDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf(initialDay.toString()) }
    var timeText by rememberSaveable { mutableStateOf("") }
    var weekdays by rememberSaveable { mutableStateOf(setOf(initialDay.dayOfWeek.value)) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val start = LocalDate.parse(startDate)
    val time = timeText.takeIf(String::isNotEmpty)?.let(LocalTime::parse)
    val selectedWeekdays = weekdays.map(DayOfWeek::of).toSet()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Nova rotina", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))

        FormField("Nome") {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().testTag("routine-title"),
                placeholder = { Text("Ex.: Alongar 10 minutos") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = formFieldColors(),
            )
        }

        FormField("Começa em") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routine-start-date")
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlannerExtras.palette.surface)
                    .border(1.dp, PlannerExtras.palette.line, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { pickingDate = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    start.format(StartDateFormatter),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp),
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        FormField("Horário") {
            OptionalTimePickerField(
                value = time,
                onValueChange = { timeText = formatTime(it) },
                label = "Horário opcional",
                tag = "routine-time",
            )
        }

        FormField("Repetir") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlannerChip(
                    text = "Todo dia",
                    selected = selectedWeekdays.size == DayOfWeek.entries.size,
                    onClick = {
                        weekdays = if (selectedWeekdays.size == DayOfWeek.entries.size) emptySet()
                        else DayOfWeek.entries.map(DayOfWeek::getValue).toSet()
                    },
                )
                DayOfWeek.entries.forEach { weekday ->
                    PlannerChip(
                        text = weekday.getDisplayName(TextStyle.SHORT, PtBr).removeSuffix(".").replaceFirstChar(Char::uppercase),
                        selected = weekday in selectedWeekdays,
                        onClick = {
                            weekdays = if (weekday.value in weekdays) weekdays - weekday.value
                            else weekdays + weekday.value
                        },
                    )
                }
            }
        }

        Button(
            enabled = title.trim().isNotEmpty() && selectedWeekdays.isNotEmpty(),
            onClick = { onSave(RoutineDraft(title.trim(), selectedWeekdays, start, time)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Salvar rotina", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
        }
    }

    if (pickingDate) {
        StartDatePickerDialog(
            initial = start,
            onDismiss = { pickingDate = false },
            onConfirm = {
                startDate = it.toString()
                pickingDate = false
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun StartDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val ptContext = remember(context) {
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(PtBr) },
        )
    }
    CompositionLocalProvider(
        LocalContext provides ptContext,
        LocalConfiguration provides ptContext.resources.configuration,
    ) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        val palette = PlannerExtras.palette
        DatePickerDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(28.dp),
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let {
                            onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                    },
                ) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        ) {
            DatePicker(
                state = state,
                title = {
                    Text(
                        "Escolha a data",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = palette.secondaryInk,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    )
                },
                headline = {
                    val picked = state.selectedDateMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    Text(
                        picked?.format(StartDateFormatter) ?: "Sem data",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background,
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary,
                    todayContentColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = palette.secondaryInk,
                    headlineContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
