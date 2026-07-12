package dev.guilhermeluan.planner.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.guilhermeluan.planner.ui.components.PlannerPrimaryButton

@Composable
fun AccountSettingsScreen(
    currentTimezone: String,
    onSaveTimezone: (String) -> Unit,
    onBack: () -> Unit,
    detectedTimezone: String = AccountTimezones.detected(),
    modifier: Modifier = Modifier,
) {
    var timezone by rememberSaveable(currentTimezone) { mutableStateOf(currentTimezone) }
    val normalized = timezone.trim()
    val error = if (normalized.isNotEmpty() && !AccountTimezones.isValid(normalized)) {
        "Fuso da Conta inválido"
    } else {
        null
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Voltar")
                }
                Text(
                    text = "Configurações da Conta",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Fuso da Conta", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Dias, horários e notificações usam este fuso.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = timezone,
                        onValueChange = { timezone = it },
                        modifier = Modifier.fillMaxWidth().testTag("account-timezone"),
                        label = { Text("Ex.: America/Sao_Paulo") },
                        isError = error != null,
                        supportingText = {
                            Text(error ?: "Detectado neste dispositivo: $detectedTimezone")
                        },
                        singleLine = true,
                    )
                    TextButton(
                        onClick = { timezone = detectedTimezone },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Usar fuso detectado")
                    }
                    PlannerPrimaryButton(
                        text = "Salvar fuso",
                        onClick = { onSaveTimezone(normalized) },
                        enabled = normalized.isNotEmpty() && error == null,
                    )
                }
            }
        }
    }
}
