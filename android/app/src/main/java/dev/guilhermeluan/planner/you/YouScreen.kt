package dev.guilhermeluan.planner.you

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.R
import dev.guilhermeluan.planner.session.AccountTimezones
import dev.guilhermeluan.planner.tasks.ArchivedItems
import dev.guilhermeluan.planner.ui.components.PlannerScene
import dev.guilhermeluan.planner.ui.components.PtBr
import dev.guilhermeluan.planner.ui.components.SceneColors
import dev.guilhermeluan.planner.ui.theme.PlannerExtras
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/** O que a aba Você mostra; os números de [consistency] chegam depois de calculados. */
data class YouUiState(
    val name: String,
    val timezone: String,
    val memberSince: YearMonth?,
    val today: LocalDate,
    val consistency: Consistency?,
    /** Nulo até o sistema ser consultado, para não afirmar "Ativas" sem saber. */
    val notificationsEnabled: Boolean?,
    val lastBackup: LocalDate?,
    val archived: ArchivedItems,
)

internal val SceneTitleInk = Color(0xFFFFF4F8)
private val SceneSubtitleInk = Color(0xFFF6C9DA)
internal val SettingsSubtitleInk = Color(0xFFFFE1EA)
private val ScenePillFill = Color.White.copy(alpha = 0.16f)

/** Tons dos blocos de constância e dos cartões; os claros são os do Figma, os escuros seguem os tokens noturnos. */
internal data class YouTones(
    val waterTile: Color,
    val waterInk: Color,
    val doseTile: Color,
    val doseInk: Color,
    val routineTile: Color,
    val routineInk: Color,
    val tileLabel: Color,
    val cardLine: Color,
    val rowDivider: Color,
) {
    companion object {
        val Light = YouTones(
            waterTile = Color(0xFFE4E9FB), waterInk = Color(0xFF3D4C93),
            doseTile = Color(0xFFFDE6DA), doseInk = Color(0xFF8A4526),
            routineTile = Color(0xFFFFE3EE), routineInk = Color(0xFFA83262),
            tileLabel = Color(0xFF5E4552),
            cardLine = Color(0xFFF6DCE6), rowDivider = Color(0xFFF6E4EB),
        )
        val Dark = YouTones(
            waterTile = Color(0xFF2B2740), waterInk = Color(0xFFC9D0F5),
            doseTile = Color(0xFF3A2620), doseInk = Color(0xFFFFB48A),
            routineTile = Color(0xFF35212A), routineInk = Color(0xFFFF8FBA),
            tileLabel = Color(0xFFD8B8C5),
            cardLine = Color(0xFF5F3D4B), rowDivider = Color(0xFF4A3039),
        )
    }
}

internal val youTones: YouTones
    @Composable @ReadOnlyComposable get() =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) YouTones.Dark else YouTones.Light

/** A aba Você: perfil e constância, com as configurações numa tela à parte. */
@Composable
fun YouTab(
    state: YouUiState,
    onSaveName: (String) -> Unit,
    onSaveTimezone: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreTask: (String) -> Unit,
    onRestoreRoutine: (String) -> Unit,
    onRestoreMedicine: (String) -> Unit,
    modifier: Modifier = Modifier,
    detectedTimezone: String = AccountTimezones.detected(),
) {
    var inSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = inSettings) { inSettings = false }
    if (inSettings) {
        SettingsScreen(
            state = state,
            onBack = { inSettings = false },
            onSaveName = onSaveName,
            onSaveTimezone = onSaveTimezone,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onExportBackup = onExportBackup,
            onRestoreTask = onRestoreTask,
            onRestoreRoutine = onRestoreRoutine,
            onRestoreMedicine = onRestoreMedicine,
            detectedTimezone = detectedTimezone,
            modifier = modifier,
        )
    } else {
        YouScreen(state, onOpenSettings = { inSettings = true }, modifier)
    }
}

/** Tela "04 · Você" do Figma: inicial em destaque, nome, desde quando e "Sua constância". */
@Composable
fun YouScreen(
    state: YouUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize().testTag("you-screen"), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            PlannerScene(SceneColors.Account, height = 260.dp)
            Column(
                Modifier.fillMaxWidth().padding(top = 52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.name.trim().take(1).uppercase().ifEmpty { "?" },
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 32.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    state.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = SceneTitleInk,
                )
                state.memberSince?.let {
                    Text(
                        "No Planner desde ${it.month.getDisplayName(TextStyle.FULL, PtBr)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = SceneSubtitleInk,
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 20.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ScenePillFill)
                    .clickable(role = Role.Button, onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_settings_gear),
                    contentDescription = "Abrir configurações",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 232.dp)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ConsistencySection(state)
                SettingsRow(onOpenSettings)
            }
        }
    }
}

@Composable
private fun ConsistencySection(state: YouUiState) {
    val tones = youTones
    val consistency = state.consistency
    val month = state.today.month.getDisplayName(TextStyle.FULL, PtBr)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sua constância", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val streak = consistency?.waterStreakDays ?: 0
            ConsistencyTile(
                value = "$streak ${if (streak == 1) "dia" else "dias"}",
                label = "seguidos batendo a meta de água",
                tile = tones.waterTile, ink = tones.waterInk, modifier = Modifier.weight(1f).testTag("consistency-water"),
            )
            ConsistencyTile(
                value = consistency?.onTimeDosePercent?.let { "$it%" } ?: "—",
                label = "das doses de $month tomadas na hora",
                tile = tones.doseTile, ink = tones.doseInk, modifier = Modifier.weight(1f).testTag("consistency-doses"),
            )
            ConsistencyTile(
                value = (consistency?.routinesDoneThisMonth ?: 0).toString(),
                label = "rotinas concluídas este mês",
                tile = tones.routineTile, ink = tones.routineInk, modifier = Modifier.weight(1f).testTag("consistency-routines"),
            )
        }
    }
}

@Composable
private fun ConsistencyTile(value: String, label: String, tile: Color, ink: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(tile).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = ink)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = youTones.tileLabel,
        )
    }
}

@Composable
private fun SettingsRow(onOpen: () -> Unit) {
    val palette = PlannerExtras.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .border(1.dp, youTones.cardLine, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Configurações", style = MaterialTheme.typography.titleMedium, color = palette.wine)
            Text("Conta, lembretes e backup", style = MaterialTheme.typography.bodySmall, color = palette.mutedInk)
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}
