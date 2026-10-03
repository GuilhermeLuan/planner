package dev.guilhermeluan.planner.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.session.AccountTimezones
import dev.guilhermeluan.planner.tasks.ArchivedItems
import dev.guilhermeluan.planner.ui.components.FormField
import dev.guilhermeluan.planner.ui.components.PlannerFormSheet
import dev.guilhermeluan.planner.ui.components.PlannerPrimaryButton
import dev.guilhermeluan.planner.ui.components.formFieldColors
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import dev.guilhermeluan.planner.ui.theme.youTones

@Composable
private fun SheetColumn(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))
        content()
    }
}

@Composable
internal fun EditNameSheet(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    val normalized = name.trim()
    PlannerFormSheet(onDismiss) {
        SheetColumn("Seu nome") {
            FormField("Nome") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("account-name"),
                    isError = normalized.isEmpty(),
                    supportingText = if (normalized.isEmpty()) ({ Text("Informe seu nome") }) else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = formFieldColors(),
                )
            }
            PlannerPrimaryButton("Salvar nome", onClick = { onSave(normalized) }, enabled = normalized.isNotEmpty())
        }
    }
}

@Composable
internal fun EditTimezoneSheet(
    currentTimezone: String,
    detectedTimezone: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var timezone by rememberSaveable { mutableStateOf(currentTimezone) }
    val normalized = timezone.trim()
    val invalid = normalized.isNotEmpty() && !AccountTimezones.isValid(normalized)
    PlannerFormSheet(onDismiss) {
        SheetColumn("Fuso da Conta") {
            Text(
                "Dias, horários e notificações usam este fuso.",
                style = MaterialTheme.typography.bodyMedium,
                color = PlannerExtras.palette.secondaryInk,
            )
            FormField("Ex.: America/Sao_Paulo") {
                OutlinedTextField(
                    value = timezone,
                    onValueChange = { timezone = it },
                    modifier = Modifier.fillMaxWidth().testTag("account-timezone"),
                    isError = invalid,
                    supportingText = { Text(if (invalid) "Fuso da Conta inválido" else "Detectado neste dispositivo: $detectedTimezone") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = formFieldColors(),
                )
            }
            TextButton(onClick = { timezone = detectedTimezone }) { Text("Usar fuso detectado") }
            PlannerPrimaryButton("Salvar fuso", onClick = { onSave(normalized) }, enabled = normalized.isNotEmpty() && !invalid)
        }
    }
}

/** Folha "Itens arquivados": Tarefas, Rotinas e Remédios juntos, cada um com a ação de restaurar (ADR 0010). */
@Composable
internal fun ArchivedItemsSheet(
    items: ArchivedItems,
    onDismiss: () -> Unit,
    onRestoreTask: (String) -> Unit,
    onRestoreRoutine: (String) -> Unit,
    onRestoreMedicine: (String) -> Unit,
) {
    PlannerFormSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetColumn("Itens arquivados") {
                if (items.isEmpty) {
                    Text("Nada arquivado", style = MaterialTheme.typography.bodyMedium, color = PlannerExtras.palette.mutedInk)
                }
                ArchivedGroup("Tarefas", items.tasks.map { ArchivedEntry(it.id, it.title, it.day.toString()) }, "restore-task", onRestoreTask)
                ArchivedGroup("Rotinas", items.routines.map { ArchivedEntry(it.id, it.title) }, "restore-routine", onRestoreRoutine)
                ArchivedGroup("Remédios", items.medicines.map { ArchivedEntry(it.id, it.name) }, "restore-medicine", onRestoreMedicine)
            }
        }
    }
}

private data class ArchivedEntry(val id: String, val label: String, val detail: String? = null)

@Composable
private fun ArchivedGroup(
    title: String,
    entries: List<ArchivedEntry>,
    tagPrefix: String,
    onRestore: (String) -> Unit,
) {
    if (entries.isEmpty()) return
    val palette = PlannerExtras.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = palette.secondaryInk)
        entries.forEach { (id, label, detail) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.surface)
                    .border(1.dp, youTones.cardLine, RoundedCornerShape(20.dp))
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = palette.wine)
                    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.mutedInk) }
                }
                TextButton(onClick = { onRestore(id) }, modifier = Modifier.testTag("$tagPrefix-$id")) {
                    Text("Restaurar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
