package dev.guilhermeluan.planner.you

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Confirmação antes de substituir o Planner atual pelo conteúdo de um backup. */
@Composable
fun ImportBackupDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar backup?") },
        text = { Text("O Planner atual será apagado e substituído pelo do backup. Isso não pode ser desfeito.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Substituir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
