package dev.guilhermeluan.planner.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import dev.guilhermeluan.planner.tasks.DayPlan
import dev.guilhermeluan.planner.tasks.PlannedRoutineOccurrence
import dev.guilhermeluan.planner.tasks.PlannerTask
import dev.guilhermeluan.planner.tasks.RoutineDraft
import dev.guilhermeluan.planner.tasks.RoutineOccurrenceStatus
import dev.guilhermeluan.planner.tasks.TaskDraft
import dev.guilhermeluan.planner.tasks.TaskStatus
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class DayUiState(
    val selectedDay: LocalDate,
    val plan: DayPlan,
    val isLoading: Boolean = false,
    val syncError: String? = null,
    val pendingOperations: Int = 0,
)

@Composable
fun DayScreen(
    state: DayUiState,
    onSelectDay: (LocalDate) -> Unit,
    onCreateTask: (TaskDraft) -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onLogout: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCreateRoutine: (RoutineDraft) -> Unit = {},
    onToggleRoutine: (String, LocalDate, RoutineOccurrenceStatus) -> Unit = { _, _, _ -> },
    onEditTask: (String, String, LocalTime?) -> Unit = { _, _, _ -> },
    onRescheduleTask: (String, LocalDate) -> Unit = { _, _ -> },
    onArchiveTask: (String) -> Unit = {},
    onRestoreTask: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showCreateTask by rememberSaveable { mutableStateOf(false) }
    var draftTitle by rememberSaveable { mutableStateOf("") }
    var draftTime by rememberSaveable { mutableStateOf("") }
    var showCreateRoutine by rememberSaveable { mutableStateOf(false) }
    var routineTitle by rememberSaveable { mutableStateOf("") }
    var routineStartDate by rememberSaveable { mutableStateOf(state.selectedDay.toString()) }
    var routineTime by rememberSaveable { mutableStateOf("") }
    var routineWeekdays by remember { mutableStateOf(setOf(state.selectedDay.dayOfWeek)) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<PlannerTask?>(null) }
    var reschedulingTask by remember { mutableStateOf<PlannerTask?>(null) }
    val selectedDay = state.selectedDay
    val plan = state.plan
    val scheduledTasks = plan.tasks.filter { it.time != null }
    val untimedTasks = plan.tasks.filter { it.time == null }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    DayHeader(
                        selectedDay = selectedDay,
                        onPrevious = { onSelectDay(selectedDay.minusDays(1)) },
                        onNext = { onSelectDay(selectedDay.plusDays(1)) },
                        onOpenSettings = onOpenSettings,
                        onOpenCalendar = { showCalendar = true },
                    )
                }
                item {
                    DayRibbon(
                        selectedDay = selectedDay,
                        onSelectDay = onSelectDay,
                    )
                }
                item {
                    DaySummary(
                        plan = plan,
                        pendingOperations = state.pendingOperations,
                        syncError = state.syncError,
                    )
                }
                item {
                    DaySection(title = "Rotinas") {
                        Button(
                            onClick = {
                                routineTitle = ""
                                routineStartDate = selectedDay.toString()
                                routineTime = ""
                                routineWeekdays = setOf(selectedDay.dayOfWeek)
                                showCreateRoutine = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("Nova Rotina")
                        }
                        if (plan.routines.isEmpty()) {
                            EmptySectionText("Nenhuma rotina planejada para este dia")
                        } else {
                            plan.routines.forEach { routine ->
                                RoutineRow(routine, onToggleRoutine)
                            }
                        }
                    }
                }
                if (scheduledTasks.isNotEmpty()) {
                    item {
                        DaySection(title = "Tarefas com horário") {
                            scheduledTasks.forEach { task ->
                                TaskRow(
                                    task = task,
                                    onToggleTask = onToggleTask,
                                    onEditTask = { editingTask = it },
                                    onRescheduleTask = { reschedulingTask = it },
                                    onArchiveTask = onArchiveTask,
                                )
                            }
                        }
                    }
                } else {
                    item {
                        DaySection(title = "Tarefas com horário") {
                            EmptySectionText("Nenhuma tarefa com horário")
                        }
                    }
                }
                item {
                    DaySection(title = "Sem horário") {
                        if (untimedTasks.isEmpty()) {
                            EmptySectionText("Tudo em dia por enquanto")
                        } else {
                            untimedTasks.forEach { task ->
                                TaskRow(
                                    task = task,
                                    onToggleTask = onToggleTask,
                                    onEditTask = { editingTask = it },
                                    onRescheduleTask = { reschedulingTask = it },
                                    onArchiveTask = onArchiveTask,
                                )
                            }
                        }
                    }
                }
                if (plan.archivedTasks.isNotEmpty()) {
                    item {
                        DaySection(title = "Arquivadas") {
                            plan.archivedTasks.forEach { task ->
                                ArchivedTaskRow(task, onRestoreTask)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showCreateTask = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Nova Tarefa", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showCreateTask) {
        AlertDialog(
            onDismissRequest = {
                showCreateTask = false
                draftTitle = ""
            },
            title = { Text("Nova Tarefa") },
            text = {
                val parsedTime = draftTime.trim().takeIf(String::isNotEmpty)
                    ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                val invalidTime = draftTime.isNotBlank() && parsedTime == null
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        modifier = Modifier.fillMaxWidth().testTag("task-title"),
                        label = { Text("Título da Tarefa") },
                        singleLine = true,
                    )
                    OptionalTimePickerField(
                        value = parsedTime,
                        onValueChange = { draftTime = formatTime(it) },
                        label = "Horário opcional",
                        tag = "task-time",
                    )
                }
            },
            confirmButton = {
                val parsedTime = draftTime.trim().takeIf(String::isNotEmpty)
                    ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                val invalidTime = draftTime.isNotBlank() && parsedTime == null
                TextButton(
                    enabled = draftTitle.trim().isNotEmpty() && !invalidTime,
                    onClick = {
                        onCreateTask(TaskDraft(draftTitle.trim(), selectedDay, parsedTime))
                        showCreateTask = false
                        draftTitle = ""
                        draftTime = ""
                    },
                ) {
                    Text("Adicionar ao Dia")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateTask = false
                    draftTitle = ""
                    draftTime = ""
                }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showCreateRoutine) {
        val parsedStart = runCatching { LocalDate.parse(routineStartDate.trim()) }.getOrNull()
        val parsedTime = routineTime.trim().takeIf(String::isNotEmpty)?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        }
        val startError = if (parsedStart == null) "Use uma data como 2026-07-11" else null
        AlertDialog(
            onDismissRequest = { showCreateRoutine = false },
            title = { Text("Nova Rotina") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = routineTitle,
                        onValueChange = { routineTitle = it },
                        modifier = Modifier.fillMaxWidth().testTag("routine-title"),
                        label = { Text("Título da Rotina") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = routineStartDate,
                        onValueChange = { routineStartDate = it },
                        modifier = Modifier.fillMaxWidth().testTag("routine-start-date"),
                        label = { Text("Começa em (AAAA-MM-DD)") },
                        isError = startError != null,
                        supportingText = { if (startError != null) Text(startError) },
                        singleLine = true,
                    )
                    OptionalTimePickerField(
                        value = parsedTime,
                        onValueChange = { routineTime = formatTime(it) },
                        label = "Horário opcional",
                        tag = "routine-time",
                    )
                    Text("Dias da semana", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { weekday ->
                            FilterChip(
                                selected = weekday in routineWeekdays,
                                onClick = {
                                    routineWeekdays = if (weekday in routineWeekdays) {
                                        routineWeekdays - weekday
                                    } else {
                                        routineWeekdays + weekday
                                    }
                                },
                                label = {
                                    Text(weekday.getDisplayName(TextStyle.NARROW, Locale("pt", "BR")))
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = routineTitle.trim().isNotEmpty() &&
                        parsedStart != null && routineWeekdays.isNotEmpty(),
                    onClick = {
                        onCreateRoutine(
                            RoutineDraft(
                                title = routineTitle.trim(),
                                weekdays = routineWeekdays,
                                startDate = parsedStart ?: selectedDay,
                                time = parsedTime,
                            ),
                        )
                        showCreateRoutine = false
                    },
                ) {
                    Text("Salvar Rotina")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRoutine = false }) { Text("Cancelar") }
            },
        )
    }

    if (showCalendar) {
        CalendarDialog(
            selectedDay = selectedDay,
            onSelectDay = {
                onSelectDay(it)
                showCalendar = false
            },
            onDismiss = { showCalendar = false },
        )
    }

    editingTask?.let { task ->
        EditTaskDialog(
            task = task,
            onDismiss = { editingTask = null },
            onSave = { title, time ->
                onEditTask(task.id, title, time)
                editingTask = null
            },
        )
    }

    reschedulingTask?.let { task ->
        RescheduleTaskDialog(
            task = task,
            onDismiss = { reschedulingTask = null },
            onSave = { day ->
                onRescheduleTask(task.id, day)
                reschedulingTask = null
            },
        )
    }
}

@Composable
private fun DayHeader(
    selectedDay: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 24.dp, top = 28.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PLANNER · HOJE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = selectedDay.format(
                        DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("pt", "BR")),
                    ),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Row {
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Configurações")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Dia anterior")
            }
            Text(
                text = "Navegar pelos dias",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenCalendar) { Text("Calendário") }
            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Próximo dia")
            }
        }
    }
}

@Composable
private fun DayRibbon(
    selectedDay: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        (-3L..3L).forEach { offset ->
            val day = selectedDay.plusDays(offset)
            val selected = day == selectedDay
            TextButton(
                onClick = { onSelectDay(day) },
                modifier = Modifier.size(width = 42.dp, height = 64.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "BR"))
                            .replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySummary(plan: DayPlan, pendingOperations: Int, syncError: String?) {
    val completed = plan.tasks.count { it.status == TaskStatus.DONE }
    Surface(
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Fita do Dia", style = MaterialTheme.typography.titleLarge)
                Text(
                    syncError ?: if (pendingOperations > 0) {
                        "$pendingOperations alteração(ões) aguardando sincronização"
                    } else {
                        "Uma sequência leve para hoje"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (syncError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "$completed/${plan.tasks.size}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DaySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
            },
        )
    }
}

@Composable
private fun EmptySectionText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RoutineRow(
    routine: PlannedRoutineOccurrence,
    onToggleRoutine: (String, LocalDate, RoutineOccurrenceStatus) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = routine.status == RoutineOccurrenceStatus.DONE,
            onCheckedChange = { checked ->
                onToggleRoutine(
                    routine.routineId,
                    routine.day,
                    if (checked) RoutineOccurrenceStatus.DONE else RoutineOccurrenceStatus.PENDING,
                )
            },
        )
        Column {
            Text(
                routine.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (routine.status == RoutineOccurrenceStatus.DONE) {
                    TextDecoration.LineThrough
                } else {
                    null
                },
            )
            routine.time?.let {
                Text(it.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: PlannerTask,
    onToggleTask: (String, Boolean) -> Unit,
    onEditTask: (PlannerTask) -> Unit,
    onRescheduleTask: (PlannerTask) -> Unit,
    onArchiveTask: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.status == TaskStatus.DONE,
            onCheckedChange = { checked -> onToggleTask(task.id, checked) },
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
                color = if (task.status == TaskStatus.DONE) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            task.time?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Ações de ${task.title}")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Editar Tarefa") },
                    onClick = {
                        showMenu = false
                        onEditTask(task)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Reagendar") },
                    onClick = {
                        showMenu = false
                        onRescheduleTask(task)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Arquivar") },
                    onClick = {
                        showMenu = false
                        onArchiveTask(task.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun ArchivedTaskRow(task: PlannerTask, onRestoreTask: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            task.time?.let {
                Text(it.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = { onRestoreTask(task.id) }) { Text("Restaurar") }
    }
}

@Composable
private fun CalendarDialog(
    selectedDay: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selectedDay).toString()) }
    val month = runCatching { YearMonth.parse(monthText) }.getOrDefault(YearMonth.from(selectedDay))
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val cells = buildList<LocalDate?> {
        repeat(firstOffset) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
        while (size % 7 != 0) add(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calendário mensal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "Mês anterior")
                    }
                    Text(
                        text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR"))),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "Próximo mês")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { label ->
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(Modifier.weight(1f).size(42.dp))
                            } else {
                                TextButton(
                                    onClick = { onSelectDay(day) },
                                    modifier = Modifier.weight(1f).size(42.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (day == selectedDay) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                    ),
                                ) {
                                    Text(day.dayOfMonth.toString())
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun EditTaskDialog(
    task: PlannerTask,
    onDismiss: () -> Unit,
    onSave: (String, LocalTime?) -> Unit,
) {
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var timeText by rememberSaveable(task.id) { mutableStateOf(task.time?.toString().orEmpty()) }
    val time = timeText.trim().takeIf(String::isNotEmpty)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val invalidTime = timeText.isNotBlank() && time == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Tarefa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().testTag("edit-task-title"),
                    label = { Text("Título") },
                    singleLine = true,
                )
                OptionalTimePickerField(
                    value = time,
                    onValueChange = { timeText = formatTime(it) },
                    label = "Horário opcional",
                    tag = "edit-task-time",
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotEmpty() && !invalidTime,
                onClick = { onSave(title.trim(), time) },
            ) { Text("Salvar alterações") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun RescheduleTaskDialog(
    task: PlannerTask,
    onDismiss: () -> Unit,
    onSave: (LocalDate) -> Unit,
) {
    var dayText by rememberSaveable(task.id) { mutableStateOf(task.day.toString()) }
    val day = runCatching { LocalDate.parse(dayText.trim()) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reagendar Tarefa") },
        text = {
            OutlinedTextField(
                value = dayText,
                onValueChange = { dayText = it },
                modifier = Modifier.fillMaxWidth().testTag("reschedule-task-day"),
                label = { Text("Novo Dia (AAAA-MM-DD)") },
                isError = day == null,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = day != null, onClick = { day?.let(onSave) }) {
                Text("Reagendar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun formatTime(time: LocalTime?): String = time?.let {
    String.format(Locale.ROOT, "%02d:%02d", it.hour, it.minute)
}.orEmpty()
