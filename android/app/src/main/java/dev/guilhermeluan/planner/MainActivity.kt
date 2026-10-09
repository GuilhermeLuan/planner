package dev.guilhermeluan.planner

import android.Manifest
import android.content.ContentValues.TAG
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.guilhermeluan.planner.day.DayScreen
import dev.guilhermeluan.planner.day.DaySummary
import dev.guilhermeluan.planner.day.DayViewModel
import dev.guilhermeluan.planner.medicines.MedicinesScreen
import dev.guilhermeluan.planner.medicines.MedicinesViewModel
import dev.guilhermeluan.planner.session.OnboardingScreen
import dev.guilhermeluan.planner.session.PlannerAppUiState
import dev.guilhermeluan.planner.session.PlannerViewModel
import dev.guilhermeluan.planner.ui.navigation.PlannerTab
import dev.guilhermeluan.planner.ui.navigation.PlannerTabHost
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import dev.guilhermeluan.planner.water.WaterScreen
import dev.guilhermeluan.planner.water.WaterViewModel
import dev.guilhermeluan.planner.you.ImportBackupDialog
import dev.guilhermeluan.planner.you.YouTab
import dev.guilhermeluan.planner.backup.Backup
import dev.guilhermeluan.planner.you.YouViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.guilhermeluan.planner.notifications.ExactAlarmPermission
import dev.guilhermeluan.planner.notifications.NotificationPermission
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: PlannerViewModel by viewModels { PlannerViewModel.Factory(application as PlannerApplication) }
    private val dayViewModel: DayViewModel by viewModels { DayViewModel.Factory(application as PlannerApplication) }
    private val medicinesViewModel: MedicinesViewModel by viewModels { MedicinesViewModel.Factory(application as PlannerApplication) }
    private val waterViewModel: WaterViewModel by viewModels { WaterViewModel.Factory(application as PlannerApplication) }
    private val youViewModel: YouViewModel by viewModels { YouViewModel.Factory(application as PlannerApplication) }

    /** Para onde o widget pediu para ir; vale até a tela correspondente tratar o pedido. */
    private var destination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Depois de uma recriação (rotação) o pedido original já foi tratado.
        if (savedInstanceState == null) destination = intent.destination()
        setContent {
            PlannerTheme {
                NotificationPermissionRequester {
                    PlannerApp(
                        viewModel, dayViewModel, medicinesViewModel, waterViewModel, youViewModel,
                        destination = destination,
                        onDestinationHandled = { destination = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Um lançamento sem pedido (ícone do app) não apaga o pedido do widget ainda não tratado.
        intent.destination()?.let { destination = it }
    }

    private fun Intent.destination(): String? = getStringExtra(EXTRA_DESTINATION)

    companion object {
        /** Extra que diz a tela em que o app deve abrir; usado pelo widget de ação rápida. */
        const val EXTRA_DESTINATION = "destination"
        const val DESTINATION_NEW_TASK = "new_task"
        const val DESTINATION_MEDICINES = "medicines"
    }
}

@Composable
private fun NotificationPermissionRequester(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* notificação funciona sem a permissão, só perde o alerta */ }
    LaunchedEffect(Unit) {
        if (dev.guilhermeluan.planner.notifications.NotificationPermission.shouldRequest(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    content()
}

@Composable
private fun PlannerApp(
    viewModel: PlannerViewModel,
    dayViewModel: DayViewModel,
    medicinesViewModel: MedicinesViewModel,
    waterViewModel: WaterViewModel,
    youViewModel: YouViewModel,
    destination: String?,
    onDestinationHandled: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dayState by dayViewModel.uiState.collectAsStateWithLifecycle()
    val medicinesState by medicinesViewModel.uiState.collectAsStateWithLifecycle()
    val waterState by waterViewModel.uiState.collectAsStateWithLifecycle()
    val youState by youViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (state !is PlannerAppUiState.Ready) return@rememberLauncherForActivityResult
        viewModel.exportBackup { result ->
            result.fold(
                onSuccess = { json ->
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(json.toString(2).toByteArray())
                        }
                        youViewModel.backupSaved()
                        Toast.makeText(context, "Backup exportado com sucesso", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Falha ao escrever backup", e)
                        Toast.makeText(context, "Erro ao salvar o arquivo", Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Falha ao gerar backup", e)
                    Toast.makeText(context, "Erro ao gerar o backup", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
    // O backup lido fica aqui até a pessoa confirmar que o Planner atual será substituído.
    var pendingBackup by remember { mutableStateOf<Backup?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao ler backup", e)
            null
        }
        if (text == null) {
            Toast.makeText(context, "Erro ao ler o arquivo", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.readBackup(text) { result ->
            result.fold(
                onSuccess = { pendingBackup = it },
                onFailure = { e ->
                    Toast.makeText(context, e.message ?: "Backup inválido", Toast.LENGTH_LONG).show()
                },
            )
        }
    }
    pendingBackup?.let { backup ->
        ImportBackupDialog(
            onConfirm = {
                pendingBackup = null
                viewModel.importBackup(backup) { result ->
                    result.fold(
                        onSuccess = { imported ->
                            val timezone = imported.account.timezone
                            dayViewModel.updateTimezone(timezone)
                            medicinesViewModel.updateTimezone(timezone)
                            waterViewModel.updateTimezone(timezone)
                            Toast.makeText(context, "Backup importado", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Falha ao importar backup", e)
                            Toast.makeText(context, "Erro ao importar o backup", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            },
            onDismiss = { pendingBackup = null },
        )
    }
    when (val current = state) {
        PlannerAppUiState.Loading -> LoadingScreen()
        is PlannerAppUiState.NeedsOnboarding -> OnboardingScreen(
            timezone = current.suggestedTimezone,
            isSaving = current.isSaving,
            error = current.error,
            onCreatePlanner = viewModel::createPlanner,
        )
        is PlannerAppUiState.Ready -> {
            val localPlanner = current.localPlanner
            LaunchedEffect(localPlanner.account.id, localPlanner.planner.id) {
                dayViewModel.bind(localPlanner)
                medicinesViewModel.bind(localPlanner)
                waterViewModel.bind(localPlanner)
            }
            LaunchedEffect(localPlanner) { youViewModel.bind(localPlanner) }
            // As notificações podem ser ligadas ou desligadas fora do app; relê ao voltar para ele.
            // A permissão de alarme exato também muda fora do app; ao voltar com ela concedida, reagenda tudo.
            val scope = rememberCoroutineScope()
            var exactAlarmsBefore by rememberSaveable { mutableStateOf<Boolean?>(null) }
            LifecycleResumeEffect(Unit) {
                youViewModel.setNotificationsEnabled(NotificationPermission.areEnabled(context))
                val exactAlarms = ExactAlarmPermission.canScheduleExactAlarms(context)
                if (exactAlarms && exactAlarmsBefore == false) {
                    scope.launch { (context.applicationContext as PlannerApplication).rescheduleAll() }
                }
                exactAlarmsBefore = exactAlarms
                medicinesViewModel.setExactAlarmsAllowed(exactAlarms)
                onPauseOrDispose {}
            }
            LaunchedEffect(dayState.selectedDay) {
                medicinesViewModel.selectDay(dayState.selectedDay)
                waterViewModel.viewDay(dayState.selectedDay)
            }
            val daySummary = DaySummary.of(
                selectedDay = dayState.selectedDay,
                routines = dayState.plan.routines,
                water = waterState.viewedDay,
                doses = medicinesState.doses,
            )
            val accountTimezone = localPlanner.account.timezone
            val zone = ZoneId.of(accountTimezone)
            val clockNow by produceState(ZonedDateTime.now(Clock.systemUTC().withZone(zone)), zone) {
                while (true) {
                    value = ZonedDateTime.now(Clock.systemUTC().withZone(zone))
                    delay(60_000)
                }
            }
            val greetingTime = clockNow.toLocalTime()
            LaunchedEffect(clockNow.toLocalDate()) { waterViewModel.refreshToday() }
            PlannerTabHost(
                requestedTab = when (destination) {
                    MainActivity.DESTINATION_NEW_TASK -> PlannerTab.Today
                    MainActivity.DESTINATION_MEDICINES -> PlannerTab.Medicines
                    else -> null
                },
                onRequestedTabHandled = {
                    // A criação da Tarefa ainda precisa da tela Hoje; quem a abre limpa o pedido.
                    if (destination == MainActivity.DESTINATION_MEDICINES) onDestinationHandled()
                },
                today = { openTab ->
                    DayScreen(
                        state = dayState,
                        onSelectDay = dayViewModel::selectDay,
                        onCreateTask = dayViewModel::createTask,
                        onToggleTask = dayViewModel::toggleTask,
                        onCreateRoutine = dayViewModel::createRoutine,
                        onToggleRoutine = dayViewModel::toggleRoutine,
                        onEditTask = dayViewModel::editTask,
                        onRescheduleTask = dayViewModel::rescheduleTask,
                        onArchiveTask = dayViewModel::archiveTask,
                        onRestoreTask = dayViewModel::restoreTask,
                        summary = daySummary,
                        onOpenWater = { openTab(PlannerTab.Water) },
                        onOpenMedicines = { openTab(PlannerTab.Medicines) },
                        userName = localPlanner.account.username,
                        now = greetingTime,
                        startCreateTask = destination == MainActivity.DESTINATION_NEW_TASK,
                        onStartCreateTaskHandled = onDestinationHandled,
                    )
                },
                medicines = {
                    MedicinesScreen(
                        state = medicinesState,
                        today = clockNow.toLocalDate(),
                        zone = zone,
                        onSetDoseStatus = medicinesViewModel::setDoseStatus,
                        onSnoozeDose = medicinesViewModel::snoozeDose,
                        onCreateMedicine = medicinesViewModel::createMedicine,
                        onEditMedicine = medicinesViewModel::editMedicine,
                        onArchiveMedicine = medicinesViewModel::archiveMedicine,
                        onRestoreMedicine = medicinesViewModel::restoreMedicine,
                        onOpenAlarmSettings = { context.startActivity(ExactAlarmPermission.settingsIntent(context)) },
                    )
                },
                water = {
                    WaterScreen(
                        state = waterState,
                        onAdd = waterViewModel::add,
                        onAdjustTotal = waterViewModel::adjustTotal,
                        onSetGoal = waterViewModel::setGoal,
                        onSaveReminder = waterViewModel::saveReminderSettings,
                    )
                },
                you = {
                    YouTab(
                        state = youState,
                        onSaveName = { viewModel.saveName(it) },
                        onSaveTimezone = {
                            viewModel.saveTimezone(it)
                            dayViewModel.updateTimezone(it)
                            medicinesViewModel.updateTimezone(it)
                            waterViewModel.updateTimezone(it)
                        },
                        onOpenNotificationSettings = {
                            context.startActivity(NotificationPermission.settingsIntent(context))
                        },
                        onExportBackup = { exportLauncher.launch("planner-backup.json") },
                        onImportBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        onRestoreTask = dayViewModel::restoreTask,
                        onRestoreRoutine = dayViewModel::restoreRoutine,
                        onRestoreMedicine = medicinesViewModel::restoreMedicine,
                    )
                },
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
