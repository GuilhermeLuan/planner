package dev.guilhermeluan.planner.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import java.time.LocalTime
import dev.guilhermeluan.planner.ui.components.ClockFormatter


@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OptionalTimePickerField(
    value: LocalTime?,
    onValueChange: (LocalTime?) -> Unit,
    label: String,
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
        Icon(
            imageVector = Icons.Outlined.AccessTime,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = value?.format(ClockFormatter) ?: "Sem horário",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (isOpen) {
        TimePickerDialog(
            initial = value ?: LocalTime.of(9, 0),
            onDismiss = { isOpen = false },
            onConfirm = {
                onValueChange(it)
                isOpen = false
            },
            onClear = if (value != null) {
                {
                    onValueChange(null)
                    isOpen = false
                }
            } else null,
        )
    }
}

/** Diálogo de hora; só oferece "Sem horário" quando o campo é opcional e já tem valor (`onClear`). */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val pickerState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Escolher horário", style = MaterialTheme.typography.headlineSmall)
                TimePicker(state = pickerState)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    if (onClear != null) TextButton(onClick = onClear) { Text("Sem horário") }
                    Button(
                        onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text("Usar horário") }
                }
            }
        }
    }
}
