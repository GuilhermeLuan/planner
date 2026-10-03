package dev.guilhermeluan.planner.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.day.CompleteCircle
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.tasks.PlannerMedicine
import dev.guilhermeluan.planner.ui.components.PlannerScene
import dev.guilhermeluan.planner.ui.components.PtBr
import dev.guilhermeluan.planner.ui.components.SceneColors
import dev.guilhermeluan.planner.ui.navigation.PlannerTab
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import dev.guilhermeluan.planner.ui.theme.PlannerPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

data class MedicinesUiState(
    val selectedDay: LocalDate,
    val doses: List<PlannedDose> = emptyList(),
    val medicines: List<PlannerMedicine> = emptyList(),
    val archivedMedicines: List<PlannerMedicine> = emptyList(),
    val lastRegisteredDays: Map<String, LocalDate> = emptyMap(),
)

private val PeriodEndFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PtBr)
private val HeaderTitleInk = Color(0xFFFFF4F8)
private val HeaderSubtitleInk = Color(0xFFFFE1EA)
private val MedicineCardIcon = Color(0xFFE2A0BC)
private val NextDoseAccent = Color(0xFFB5603A)
private val NextDoseSecondary = Color(0xFF7A5A4C)

/** Aba Remédios (tela 02 do Figma): próxima Dose em destaque, Doses do Dia e lista de Remédios. */
@Composable
fun MedicinesScreen(
    state: MedicinesUiState,
    today: LocalDate,
    zone: ZoneId,
    onSetDoseStatus: (PlannedDose, DoseStatus) -> Unit,
    onSnoozeDose: (PlannedDose) -> Unit,
    onCreateMedicine: (MedicineDraft) -> Unit,
    onEditMedicine: (medicineId: String, MedicineDraft) -> Unit,
    onArchiveMedicine: (medicineId: String) -> Unit,
    onRestoreMedicine: (medicineId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingArchived by rememberSaveable { mutableStateOf(false) }
    val editing = state.medicines.firstOrNull { it.id == editingId }
    val nextDose = state.doses.firstOrNull { it.status == DoseStatus.PENDING }

    Surface(modifier.fillMaxSize().testTag("medicines-screen"), color = MaterialTheme.colorScheme.background) {
        Box {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                item { MedicinesHeader(state, today) }
                // Doses registradas de um Remédio arquivado seguem no Dia, então elas também afastam o estado vazio.
                if (state.medicines.isEmpty() && state.doses.isEmpty()) {
                    item { EmptyMedicines() }
                    if (state.archivedMedicines.isNotEmpty()) {
                        item {
                            SeeArchivedLink(Modifier.padding(horizontal = 20.dp)) { viewingArchived = true }
                        }
                    }
                } else {
                    if (state.doses.isNotEmpty()) {
                        item {
                            Box(Modifier.padding(horizontal = 20.dp)) {
                                if (nextDose != null) {
                                    NextDoseCard(
                                        nextDose,
                                        onTake = { onSetDoseStatus(nextDose, DoseStatus.TAKEN) },
                                        onSnooze = { onSnoozeDose(nextDose) },
                                    )
                                } else {
                                    AllDoneCard()
                                }
                            }
                        }
                    }
                    item {
                        Section("Doses de ${if (state.selectedDay == today) "hoje" else "este dia"}") {
                            if (state.doses.isEmpty()) {
                                Text(
                                    "Nenhuma dose neste dia",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PlannerExtras.palette.mutedInk,
                                )
                            } else {
                                DoseTimeline(state.doses, zone, onSetDoseStatus)
                            }
                        }
                    }
                    if (state.medicines.isEmpty()) {
                        item {
                            SeeArchivedLink(Modifier.padding(horizontal = 20.dp)) { viewingArchived = true }
                        }
                    } else {
                        item {
                            Section(
                                "Seus remédios",
                                action = { SeeArchivedLink { viewingArchived = true } },
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    state.medicines.forEach { MedicineCard(it) { editingId = it.id } }
                                }
                            }
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = { creating = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .testTag("new-medicine-fab"),
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Novo remédio", style = MaterialTheme.typography.labelLarge) },
            )
        }
    }

    editing?.let { medicine ->
        EditMedicineSheet(
            medicine = medicine,
            initialDay = state.selectedDay,
            today = today,
            lastRegisteredDay = state.lastRegisteredDays[medicine.id],
            onDismiss = { editingId = null },
            onSave = {
                editingId = null
                onEditMedicine(medicine.id, it)
            },
            onArchive = {
                editingId = null
                onArchiveMedicine(medicine.id)
            },
        )
    }

    if (viewingArchived) {
        ArchivedMedicinesSheet(
            medicines = state.archivedMedicines,
            onDismiss = { viewingArchived = false },
            onRestore = onRestoreMedicine,
        )
    }

    if (creating) {
        NewMedicineSheet(
            initialDay = state.selectedDay,
            onDismiss = { creating = false },
            onSave = {
                creating = false
                onCreateMedicine(it)
            },
        )
    }
}

@Composable
private fun MedicinesHeader(state: MedicinesUiState, today: LocalDate) {
    val taken = state.doses.count { it.status == DoseStatus.TAKEN }
    val total = state.doses.size
    val whenLabel = if (state.selectedDay == today) "hoje" else "neste dia"
    val subtitle = when {
        state.medicines.isEmpty() -> "Acompanhe cada dose do dia"
        total == 0 -> "Nenhuma dose $whenLabel"
        else -> "$taken de $total ${if (total == 1) "dose tomada" else "doses tomadas"} $whenLabel"
    }
    Box(Modifier.fillMaxWidth().height(208.dp).clipToBounds()) {
        PlannerScene(SceneColors.forTab(PlannerTab.Medicines), height = 230.dp)
        Column(Modifier.padding(start = 24.dp, top = 56.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Remédios", style = MaterialTheme.typography.displaySmall, color = HeaderTitleInk)
            Text(subtitle, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = HeaderSubtitleInk)
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
private fun NextDoseCard(dose: PlannedDose, onTake: () -> Unit, onSnooze: () -> Unit) {
    val palette = PlannerExtras.palette
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(palette.peach)
            .padding(20.dp)
            .testTag("next-dose"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(palette.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PlannerTab.Medicines.icon, contentDescription = null, tint = palette.alertAmber, modifier = Modifier.size(26.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Próxima dose às ${dose.time}",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = NextDoseAccent,
                )
                Text(dose.name, style = MaterialTheme.typography.titleLarge, color = palette.wine)
                Text(
                    dose.unit.format(dose.amount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = NextDoseSecondary,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onTake,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(vertical = 13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Marcar como tomado", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
            }
            Button(
                onClick = onSnooze,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Adiar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AllDoneCard() {
    val palette = PlannerExtras.palette
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(palette.peach).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Tudo certo por hoje", style = MaterialTheme.typography.titleLarge, color = palette.wine)
        Text(
            "Nenhuma dose pendente.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = palette.secondaryInk,
        )
    }
}

@Composable
private fun Section(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun DoseTimeline(doses: List<PlannedDose>, zone: ZoneId, onSetDoseStatus: (PlannedDose, DoseStatus) -> Unit) {
    val palette = PlannerExtras.palette
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .border(1.dp, palette.line, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        doses.forEachIndexed { index, dose ->
            if (index > 0) HorizontalDivider(color = palette.line)
            DoseRow(dose, zone, onSetDoseStatus)
        }
    }
}

@Composable
private fun DoseRow(dose: PlannedDose, zone: ZoneId, onSetDoseStatus: (PlannedDose, DoseStatus) -> Unit) {
    val palette = PlannerExtras.palette
    var menu by rememberSaveable { mutableStateOf(false) }
    val settled = dose.status != DoseStatus.PENDING
    val subtitle = when (dose.status) {
        DoseStatus.TAKEN -> "Tomado às ${dose.takenAt?.atZone(zone)?.toLocalTime()?.withSecond(0)?.withNano(0)}"
        DoseStatus.SKIPPED -> "Pulada"
        DoseStatus.PENDING -> dose.unit.format(dose.amount)
    }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 5.dp).testTag("dose-${dose.medicineId}-${dose.time}"),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                dose.time.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = if (settled) palette.mutedInk else MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(44.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    dose.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (settled) palette.mutedInk else palette.wine,
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.mutedInk)
            }
            val taken = dose.status == DoseStatus.TAKEN
            CompleteCircle(
                done = taken,
                label = if (taken) "Desmarcar ${dose.name} das ${dose.time}" else "Marcar ${dose.name} das ${dose.time} como tomado",
                onToggle = { onSetDoseStatus(dose, if (taken) DoseStatus.PENDING else DoseStatus.TAKEN) },
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (settled) {
                DropdownMenuItem(text = { Text("Desfazer") }, onClick = {
                    menu = false
                    onSetDoseStatus(dose, DoseStatus.PENDING)
                })
            } else {
                DropdownMenuItem(text = { Text("Marcar como tomado") }, onClick = {
                    menu = false
                    onSetDoseStatus(dose, DoseStatus.TAKEN)
                })
                DropdownMenuItem(text = { Text("Pular dose") }, onClick = {
                    menu = false
                    onSetDoseStatus(dose, DoseStatus.SKIPPED)
                })
            }
        }
    }
}

@Composable
private fun MedicineCard(medicine: PlannerMedicine, onClick: () -> Unit) {
    val palette = PlannerExtras.palette
    val low = medicine.stock?.low == true
    val shape = RoundedCornerShape(20.dp)
    val style = stockStyle(low, palette, MaterialTheme.colorScheme.primary)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(style.container)
            .border(1.dp, style.border, shape)
            .clickable(role = Role.Button, onClickLabel = "Editar ${medicine.name}", onClick = onClick)
            .padding(16.dp)
            .testTag("medicine-card-${medicine.id}")
            .semantics(mergeDescendants = true) {
                if (medicine.stock != null) stateDescription = if (low) "Estoque baixo" else "Estoque em dia"
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(medicine.name, style = MaterialTheme.typography.titleMedium, color = palette.wine)
                Text(scheduleSummary(medicine), style = MaterialTheme.typography.bodySmall, color = palette.mutedInk)
            }
            Icon(
                PlannerTab.Medicines.icon,
                contentDescription = null,
                tint = style.icon,
                modifier = Modifier.size(20.dp),
            )
        }
        medicine.stock?.let { stock ->
            StockBar(
                fraction = stock.fraction,
                fill = style.bar,
                track = style.track,
                modifier = Modifier.testTag("stock-bar-${medicine.id}"),
            )
            Text(
                if (low) "Restam ${stock.amount} · repor em breve" else medicine.unit.format(stock.amount),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = style.label,
            )
        }
    }
}

internal class StockStyle(
    val container: Color,
    val border: Color,
    val icon: Color,
    val bar: Color,
    val track: Color,
    val label: Color,
)

@Composable
private fun StockBar(fraction: Float, fill: Color, track: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(fill))
    }
}

internal fun scheduleSummary(medicine: PlannerMedicine): String {
    val times = medicine.times.sorted().joinToString(", ")
    val repeat = when (val r = medicine.repeat) {
        MedicineRepeat.Daily -> ""
        is MedicineRepeat.Weekdays -> " · " + r.days.sortedBy(DayOfWeek::getValue)
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, PtBr).removeSuffix(".") }
        is MedicineRepeat.Period -> " · até ${r.end.format(PeriodEndFormatter)}"
    }
    return "${medicine.times.size}x ao dia · $times$repeat"
}

@Composable
private fun SeeArchivedLink(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        "Ver arquivados",
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
    )
}

@Composable
private fun EmptyMedicines() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PlannerTab.Medicines.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Text("Nenhum remédio ainda", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Cadastre um remédio para acompanhar cada dose do dia.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun stockStyle(low: Boolean, palette: PlannerPalette, primary: Color) = if (low) {
    StockStyle(palette.alertSurface, palette.alertLine, palette.alertAmber, palette.alertAmber, palette.alertTrack, palette.alertAmber)
} else {
    StockStyle(palette.surface, palette.line, MedicineCardIcon, primary, palette.blush, palette.secondaryInk)
}
