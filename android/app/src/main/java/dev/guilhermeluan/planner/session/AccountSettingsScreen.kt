package dev.guilhermeluan.planner.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.guilhermeluan.planner.ui.components.PlannerScene
import dev.guilhermeluan.planner.ui.components.SceneColors
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
    currentName: String,
    currentTimezone: String,
    onSaveName: (String) -> Unit,
    onSaveTimezone: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onExportBackup: () -> Unit = {},
    detectedTimezone: String = AccountTimezones.detected(),
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    var timezone by rememberSaveable(currentTimezone) { mutableStateOf(currentTimezone) }
    val normalizedName = name.trim()
    val normalizedTimezone = timezone.trim()
    val timezoneError = if (normalizedTimezone.isNotEmpty() && !AccountTimezones.isValid(normalizedTimezone)) {
        "Fuso da Conta inválido"
    } else {
        null
    }
    val nameError = if (normalizedName.isNotEmpty()) null else "Informe seu nome"

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            PlannerScene(SceneColors.Account, height = 260.dp)
            Column(
                Modifier.fillMaxWidth().padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            currentName.trim().take(1).uppercase().ifEmpty { "?" },
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    currentName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 40.dp).size(48.dp)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
            }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 232.dp)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Seu nome", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth().testTag("account-name"),
                        label = { Text("Nome") },
                        isError = nameError != null && name.isNotEmpty(),
                        supportingText = nameError?.let { { Text(it) } },
                        singleLine = true,
                    )
                    PlannerPrimaryButton(
                        text = "Salvar nome",
                        onClick = { onSaveName(normalizedName) },
                        enabled = normalizedName.isNotEmpty(),
                    )
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
                        isError = timezoneError != null,
                        supportingText = {
                            Text(timezoneError ?: "Detectado neste dispositivo: $detectedTimezone")
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
                        onClick = { onSaveTimezone(normalizedTimezone) },
                        enabled = normalizedTimezone.isNotEmpty() && timezoneError == null,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Backup", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Exportar todos os dados do Planner em um arquivo JSON.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PlannerPrimaryButton(
                        text = "Exportar backup",
                        onClick = onExportBackup,
                    )
                }
            }
        }
        }
    }
}
