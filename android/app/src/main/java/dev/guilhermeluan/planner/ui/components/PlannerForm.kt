package dev.guilhermeluan.planner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import java.time.format.DateTimeFormatter
import java.util.Locale

val PtBr = Locale("pt", "BR")

/** Horário como o app mostra: "08:00", "21:30". */
val ClockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

@Composable
fun FormField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = PlannerExtras.palette.secondaryInk,
        )
        content()
    }
}

@Composable
fun formFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = PlannerExtras.palette.line,
    focusedContainerColor = PlannerExtras.palette.surface,
    unfocusedContainerColor = PlannerExtras.palette.surface,
)
