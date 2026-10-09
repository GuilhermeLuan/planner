package dev.guilhermeluan.planner.day

import dev.guilhermeluan.planner.ui.components.PlannerScene
import dev.guilhermeluan.planner.ui.components.SceneColors
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import dev.guilhermeluan.planner.ui.theme.plannerTones
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
    val markedDays: Set<LocalDate> = emptySet(),
)

@Composable
fun DayScreen(
    state: DayUiState,
    onSelectDay: (LocalDate) -> Unit,
    onCreateTask: (TaskDraft) -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onCreateRoutine: (RoutineDraft) -> Unit = {},
    onToggleRoutine: (String, LocalDate, RoutineOccurrenceStatus) -> Unit = { _, _, _ -> },
    onEditTask: (String, String, LocalTime?) -> Unit = { _, _, _ -> },
    onRescheduleTask: (String, LocalDate) -> Unit = { _, _ -> },
    onArchiveTask: (String) -> Unit = {},
    onRestoreTask: (String) -> Unit = {},
    summary: DaySummary = DaySummary.Empty,
    onOpenWater: () -> Unit = {},
    onOpenMedicines: () -> Unit = {},
    userName: String = "",
    now: LocalTime = LocalTime.now(),
    /** Abre direto o formulário de nova Tarefa (atalho do widget); [onStartCreateTaskHandled] avisa que já abriu. */
    startCreateTask: Boolean = false,
    onStartCreateTaskHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showCreateTask by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startCreateTask) {
        if (startCreateTask) {
            showCreateTask = true
            onStartCreateTaskHandled()
        }
    }
    var draftTitle by rememberSaveable { mutableStateOf("") }
    var draftTime by rememberSaveable { mutableStateOf("") }
    var showCreateRoutine by rememberSaveable { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<PlannerTask?>(null) }
    var reschedulingTask by remember { mutableStateOf<PlannerTask?>(null) }
    val selectedDay = state.selectedDay
    val plan = state.plan
    val scheduledTasks = plan.tasks.filter { it.time != null }
    val untimedTasks = plan.tasks.filter { it.time == null }
    val remaining = plan.tasks.count { it.status != TaskStatus.DONE }

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
                        greeting = DayGreeting.text(now, userName),
                        onOpenCalendar = { showCalendar = true },
                    )
                }
                item {
                    DayRibbon(
                        selectedDay = selectedDay,
                        markedDays = state.markedDays,
                        onSelectDay = onSelectDay,
                    )
                }
                item {
                    DaySection(title = "Seu dia") {
                        DaySummaryRow(summary, onOpenWater, onOpenMedicines)
                    }
                }
                item {
                    DaySection(
                        title = "Rotinas",
                        action = "Nova rotina",
                        onAction = {
                            showCreateRoutine = true
                        },
                    ) {
                        if (plan.routines.isEmpty()) {
                            EmptySectionText("Nenhuma rotina planejada para este dia")
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(PlannerExtras.palette.surface)
                                    .border(1.dp, PlannerExtras.palette.line, RoundedCornerShape(22.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                plan.routines.forEachIndexed { index, routine ->
                                    if (index > 0) HorizontalDivider(color = PlannerExtras.palette.line)
                                    RoutineRow(routine, onToggleRoutine)
                                }
                            }
                        }
                    }
                }
                item {
                    DaySection(
                        title = "Tarefas",
                        trailing = "$remaining ${if (remaining == 1) "restante" else "restantes"}",
                    ) {
                        if (plan.tasks.isEmpty()) {
                            EmptySectionText("Tudo em dia por enquanto")
                        } else {
                            val nextTaskId = (scheduledTasks + untimedTasks)
                                .firstOrNull { it.status != TaskStatus.DONE }?.id
                            (scheduledTasks + untimedTasks).forEach { task ->
                                TaskRow(
                                    task = task,
                                    highlighted = task.id == nextTaskId,
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

            ExtendedFloatingActionButton(
                onClick = { showCreateTask = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .testTag("new-task-fab"),
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nova tarefa", style = MaterialTheme.typography.labelLarge) },
            )
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
        NewRoutineSheet(
            initialDay = selectedDay,
            onDismiss = { showCreateRoutine = false },
            onSave = {
                onCreateRoutine(it)
                showCreateRoutine = false
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
    greeting: String,
    onOpenCalendar: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(290.dp)) {
        PlannerScene(SceneColors.Today, height = 290.dp)
        Column(
            modifier = Modifier.padding(start = 24.dp, top = 56.dp, end = 120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = selectedDay.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
                    .removeSuffix("-feira")
                    .replaceFirstChar(Char::uppercase) +
                    ", " + selectedDay.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("pt", "BR"))),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = greeting,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            TextButton(
                onClick = onOpenCalendar,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Text("Ver mês", color = Color.White, style = MaterialTheme.typography.labelMedium)
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
private fun DayRibbon(
    selectedDay: LocalDate,
    markedDays: Set<LocalDate>,
    onSelectDay: (LocalDate) -> Unit,
) {
    val palette = PlannerExtras.palette
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Week.days(Week.of(selectedDay)).forEach { day ->
            val selected = day == selectedDay
            Column(
                modifier = Modifier
                    .testTag("week-day-$day")
                    .width(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else palette.blush)
                    .clickable { onSelectDay(day) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("pt", "BR")).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = if (selected) Color(0xFFFFD6E5) else palette.mutedInk,
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
                if (day in markedDays) {
                    Box(
                        Modifier
                            .testTag("week-marker")
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary),
                    )
                } else {
                    Spacer(Modifier.size(4.dp))
                }
            }
        }
    }
}

/** Como um resumo do "Seu dia" se veste: o fundo do tile e a cor do rótulo e da barra. */
private class SummaryTileStyle(val tile: Color, val accent: Color)

/** O toque de um resumo que leva a outra aba; o rótulo diz para onde. */
private class SummaryTileAction(val label: String, val onClick: () -> Unit)

@Composable
private fun DaySummaryRow(
    summary: DaySummary,
    onOpenWater: () -> Unit,
    onOpenMedicines: () -> Unit,
) {
    val palette = PlannerExtras.palette
    val tones = plannerTones
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryTile(
            "summary-routines", "Rotinas", summary.routines,
            SummaryTileStyle(tones.routineTile, palette.raspberry), Modifier.weight(1f),
        )
        SummaryTile(
            "summary-water", "Água", summary.water,
            SummaryTileStyle(tones.waterTile, palette.waterLavender), Modifier.weight(1f),
            action = SummaryTileAction("Abrir a aba Água", onOpenWater),
        )
        SummaryTile(
            "summary-medicines", "Remédios", summary.medicines,
            SummaryTileStyle(tones.doseTile, tones.doseLabel), Modifier.weight(1f),
            action = SummaryTileAction("Abrir a aba Remédios", onOpenMedicines),
        )
    }
}

@Composable
private fun SummaryTile(
    tag: String,
    label: String,
    item: SummaryItem,
    style: SummaryTileStyle,
    modifier: Modifier = Modifier,
    action: SummaryTileAction? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .testTag(tag)
            .clip(shape)
            .background(style.tile)
            .let { if (action != null) it.clickable(onClickLabel = action.label, role = Role.Button, onClick = action.onClick) else it }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = style.accent)
        Text(
            item.value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(PlannerExtras.palette.surface)) {
            Box(
                Modifier
                    .fillMaxWidth(item.progress)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(style.accent),
            )
        }
    }
}

@Composable
private fun DaySection(
    title: String,
    trailing: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            trailing?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = PlannerExtras.palette.mutedInk,
                )
            }
            action?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onAction).padding(vertical = 8.dp),
                )
            }
        }
        content()
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
    val done = routine.status == RoutineOccurrenceStatus.DONE
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompleteCircle(
            done = done,
            label = "Concluir ${routine.title}",
            onToggle = { checked ->
                onToggleRoutine(
                    routine.routineId,
                    routine.day,
                    if (checked) RoutineOccurrenceStatus.DONE else RoutineOccurrenceStatus.PENDING,
                )
            },
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                routine.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (done) PlannerExtras.palette.mutedInk else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                routine.time?.toString() ?: "sem horário",
                style = MaterialTheme.typography.bodySmall,
                color = PlannerExtras.palette.mutedInk,
            )
        }
    }
}

@Composable
internal fun CompleteCircle(done: Boolean, label: String, onToggle: (Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) accent else Color.Transparent)
                .border(2.dp, if (done) accent else PlannerExtras.palette.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: PlannerTask,
    highlighted: Boolean,
    onToggleTask: (String, Boolean) -> Unit,
    onEditTask: (PlannerTask) -> Unit,
    onRescheduleTask: (PlannerTask) -> Unit,
    onArchiveTask: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val palette = PlannerExtras.palette
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = task.time?.toString() ?: "Livre",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(42.dp),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .background(if (highlighted) palette.blush else palette.surface)
                .border(1.dp, palette.line, shape)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompleteCircle(
                done = task.status == TaskStatus.DONE,
                label = "Concluir ${task.title}",
                onToggle = { checked -> onToggleTask(task.id, checked) },
            )
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
                color = if (task.status == TaskStatus.DONE) palette.mutedInk else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Ações de ${task.title}", tint = palette.mutedInk)
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

internal fun formatTime(time: LocalTime?): String = time?.let {
    String.format(Locale.ROOT, "%02d:%02d", it.hour, it.minute)
}.orEmpty()
