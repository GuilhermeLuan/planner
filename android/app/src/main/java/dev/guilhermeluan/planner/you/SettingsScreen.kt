package dev.guilhermeluan.planner.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.R
import dev.guilhermeluan.planner.ui.components.PlannerScene
import dev.guilhermeluan.planner.ui.components.PtBr
import dev.guilhermeluan.planner.ui.components.SceneColors
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import java.time.format.DateTimeFormatter

private enum class SettingsSheet { Name, Timezone, Archived }

private val LastBackupFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", PtBr)

/** Tela "04b · Configurações" do Figma: Conta e Lembretes e dados. */
@Composable
fun SettingsScreen(
    state: YouUiState,
    onBack: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveTimezone: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreTask: (String) -> Unit,
    onRestoreRoutine: (String) -> Unit,
    onRestoreMedicine: (String) -> Unit,
    detectedTimezone: String,
    modifier: Modifier = Modifier,
) {
    var sheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
    Surface(modifier.fillMaxSize().testTag("settings-screen"), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            PlannerScene(SceneColors.Account, height = 230.dp)
            Row(
                Modifier
                    .padding(start = 20.dp, top = 48.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(painterResource(R.drawable.ic_chevron_back), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("Você", style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = Color.White)
            }
            Column(Modifier.padding(start = 24.dp, top = 88.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Configurações", style = MaterialTheme.typography.displaySmall, color = SceneTitleInk)
                Text("Conta, lembretes e dados", style = MaterialTheme.typography.bodyMedium, color = SettingsSubtitleInk)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 178.dp)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SettingsGroup("Conta") {
                    SettingRow("Nome", state.name, "Editar") { sheet = SettingsSheet.Name }
                    SettingRow("Fuso da Conta", state.timezone.replace('_', ' '), "Alterar", last = true) { sheet = SettingsSheet.Timezone }
                }
                SettingsGroup("Lembretes e dados") {
                    SettingRow(
                        "Notificações", "Rotinas, remédios e água",
                        when (state.notificationsEnabled) {
                            true -> "Ativas"
                            false -> "Desativadas"
                            null -> "Verificar"
                        },
                        onClick = onOpenNotificationSettings,
                    )
                    SettingRow("Itens arquivados", "Tarefas, rotinas e remédios", "Ver") { sheet = SettingsSheet.Archived }
                    SettingRow(
                        "Backup",
                        state.lastBackup?.let { "Último em ${it.format(LastBackupFormatter)}" } ?: "Nenhum backup ainda",
                        "Exportar", last = true, onClick = onExportBackup,
                    )
                }
            }
        }
    }

    when (sheet) {
        SettingsSheet.Name -> EditNameSheet(
            currentName = state.name,
            onDismiss = { sheet = null },
            onSave = { sheet = null; onSaveName(it) },
        )
        SettingsSheet.Timezone -> EditTimezoneSheet(
            currentTimezone = state.timezone,
            detectedTimezone = detectedTimezone,
            onDismiss = { sheet = null },
            onSave = { sheet = null; onSaveTimezone(it) },
        )
        SettingsSheet.Archived -> ArchivedItemsSheet(
            items = state.archived,
            onDismiss = { sheet = null },
            onRestoreTask = onRestoreTask,
            onRestoreRoutine = onRestoreRoutine,
            onRestoreMedicine = onRestoreMedicine,
        )
        null -> Unit
    }
}

@Composable
private fun SettingsGroup(title: String, rows: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(PlannerExtras.palette.surface)
                .border(1.dp, youTones.cardLine, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) { rows() }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, action: String, last: Boolean = false, onClick: () -> Unit) {
    val palette = PlannerExtras.palette
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = palette.wine)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.mutedInk)
            }
            Text(action, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.primary)
        }
        if (!last) HorizontalDivider(color = youTones.rowDivider)
    }
}
