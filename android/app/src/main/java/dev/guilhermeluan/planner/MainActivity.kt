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
import dev.guilhermeluan.planner.day.DayViewModel
import dev.guilhermeluan.planner.medicines.MedicinesScreen
import dev.guilhermeluan.planner.medicines.MedicinesViewModel
import dev.guilhermeluan.planner.session.AccountSettingsScreen
import dev.guilhermeluan.planner.session.OnboardingScreen
import dev.guilhermeluan.planner.session.PlannerAppUiState
import dev.guilhermeluan.planner.session.PlannerViewModel
import dev.guilhermeluan.planner.ui.navigation.PlannerTabHost
import dev.guilhermeluan.planner.ui.theme.PlannerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PlannerViewModel by viewModels { PlannerViewModel.Factory(application as PlannerApplication) }
    private val dayViewModel: DayViewModel by viewModels { DayViewModel.Factory(application as PlannerApplication) }
    private val medicinesViewModel: MedicinesViewModel by viewModels { MedicinesViewModel.Factory(application as PlannerApplication) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlannerTheme {
                NotificationPermissionRequester {
                    PlannerApp(viewModel, dayViewModel, medicinesViewModel)
                }
            }
        }
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
private fun PlannerApp(viewModel: PlannerViewModel, dayViewModel: DayViewModel, medicinesViewModel: MedicinesViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dayState by dayViewModel.uiState.collectAsStateWithLifecycle()
    val medicinesState by medicinesViewModel.uiState.collectAsStateWithLifecycle()
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
            }
            LaunchedEffect(dayState.selectedDay) { medicinesViewModel.selectDay(dayState.selectedDay) }
            val accountTimezone = localPlanner.account.timezone
            val zone = ZoneId.of(accountTimezone)
            val clockNow by produceState(ZonedDateTime.now(Clock.systemUTC().withZone(zone)), zone) {
                while (true) {
                    value = ZonedDateTime.now(Clock.systemUTC().withZone(zone))
                    delay(60_000)
                }
            }
            val greetingTime = clockNow.toLocalTime()
            PlannerTabHost(
                today = {
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
                        userName = localPlanner.account.username,
                        now = greetingTime,
                    )
                },
                medicines = {
                    MedicinesScreen(
                        state = medicinesState,
                        today = clockNow.toLocalDate(),
                        zone = zone,
                        onSetDoseStatus = medicinesViewModel::setDoseStatus,
                        onCreateMedicine = medicinesViewModel::createMedicine,
                    )
                },
                you = {
                    AccountSettingsScreen(
                        currentName = localPlanner.account.username,
                        currentTimezone = localPlanner.account.timezone,
                        onSaveName = { viewModel.saveName(it) },
                        onSaveTimezone = { viewModel.saveTimezone(it); dayViewModel.updateTimezone(it) },
                        onExportBackup = { exportLauncher.launch("planner-backup.json") },
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
