package dev.guilhermeluan.planner.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.tasks.PlannerMedicine
import dev.guilhermeluan.planner.ui.components.PlannerFormSheet
import dev.guilhermeluan.planner.ui.theme.PlannerExtras

/** Folha "Remédios arquivados": cada Remédio com a ação de restaurar (ADR 0010). */
@Composable
internal fun ArchivedMedicinesSheet(
    medicines: List<PlannerMedicine>,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
) {
    val palette = PlannerExtras.palette
    PlannerFormSheet(onDismiss) {
        Column(
            Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Remédios arquivados", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))
            if (medicines.isEmpty()) {
                Text("Nenhum remédio arquivado", style = MaterialTheme.typography.bodyMedium, color = palette.mutedInk)
            }
            medicines.forEach { medicine ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.line, RoundedCornerShape(20.dp))
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                        .testTag("archived-medicine-${medicine.id}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(medicine.name, style = MaterialTheme.typography.titleMedium, color = palette.wine)
                        Text(scheduleSummary(medicine), style = MaterialTheme.typography.bodySmall, color = palette.mutedInk)
                    }
                    TextButton(onClick = { onRestore(medicine.id) }) {
                        Text("Restaurar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
