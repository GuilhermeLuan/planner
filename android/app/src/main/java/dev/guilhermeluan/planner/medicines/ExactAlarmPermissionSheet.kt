package dev.guilhermeluan.planner.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import dev.guilhermeluan.planner.ui.components.ClockFormatter
import dev.guilhermeluan.planner.ui.components.PlannerFormSheet
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import java.time.LocalTime

/** Folha "Permitir alarmes" (tela 06 do Figma): explica a permissão de alarme exato usando o Remédio como exemplo. */
@Composable
internal fun ExactAlarmPermissionSheet(
    medicineName: String,
    doseTime: LocalTime,
    alarmDelayMinutes: Int,
    onOpenSettings: () -> Unit,
    onNotNow: () -> Unit,
) {
    val palette = PlannerExtras.palette
    val alarmTime = doseTime.plusMinutes(alarmDelayMinutes.toLong())
    // Dispensar a folha vale como "Agora não".
    PlannerFormSheet(onNotNow) {
        Column(
            Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp).navigationBarsPadding().testTag("exact-alarm-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Permitir alarmes", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))
            Text(
                "Para tocar o alarme de $medicineName às ${alarmTime.format(ClockFormatter)} se a dose das " +
                    "${doseTime.format(ClockFormatter)} continuar pendente, o Android precisa da permissão Alarmes e lembretes.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.secondaryInk,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionStep(1, "Toque em Abrir configurações")
                PermissionStep(2, "Ative Permitir definir alarmes e lembretes")
                PermissionStep(3, "Volte para o Planner")
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Abrir configurações", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
            }
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Agora não, usar só a notificação",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Um passo numerado da folha: o número numa bolinha e o texto ao lado. */
@Composable
private fun PermissionStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
