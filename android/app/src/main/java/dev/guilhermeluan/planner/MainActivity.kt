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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.guilhermeluan.planner.day.DayScreen
import dev.guilhermeluan.planner.day.DayViewModel
import dev.guilhermeluan.planner.session.AccountSettingsScreen
import dev.guilhermeluan.planner.session.OnboardingScreen
import dev.guilhermeluan.planner.session.PlannerAppUiState
import dev.guilhermeluan.planner.session.PlannerViewModel
import dev.guilhermeluan.planner.ui.theme.PlannerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PlannerViewModel by viewModels { PlannerViewModel.Factory(application as PlannerApplication) }
    private val dayViewModel: DayViewModel by viewModels { DayViewModel.Factory(application as PlannerApplication) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlannerTheme {
                NotificationPermissionRequester {
                    PlannerApp(viewModel, dayViewModel)
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
    ) { granted ->
        dev.guilhermeluan.planner.diagnostics.PlannerDiagnostics.logger(context).log(
            "notification_permission_result",
            details = mapOf("granted" to granted.toString()),
        )
    }
    LaunchedEffect(Unit) {
        if (dev.guilhermeluan.planner.notifications.NotificationPermission.shouldRequest(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    content()
}

@Composable
private fun PlannerApp(viewModel: PlannerViewModel, dayViewModel: DayViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dayState by dayViewModel.uiState.collectAsStateWithLifecycle()
    var showAccountSettings by remember { mutableStateOf(false) }
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
    val diagnosticExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportDiagnosticLogs { result ->
            result.fold(
                onSuccess = { zip ->
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(zip) }
                        Toast.makeText(context, "Logs exportados com sucesso", Toast.LENGTH_SHORT).show()
                    } catch (error: Exception) {
                        Log.e(TAG, "Falha ao escrever diagnóstico", error)
                        Toast.makeText(context, "Erro ao salvar os logs", Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Falha ao gerar diagnóstico", error)
                    Toast.makeText(context, "Erro ao gerar os logs", Toast.LENGTH_SHORT).show()
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
            LaunchedEffect(localPlanner.account.id, localPlanner.planner.id) { dayViewModel.bind(localPlanner) }
            if (showAccountSettings) {
                AccountSettingsScreen(
                    currentName = localPlanner.account.username,
                    currentTimezone = localPlanner.account.timezone,
                    onSaveName = { viewModel.saveName(it) },
                    onSaveTimezone = { viewModel.saveTimezone(it); dayViewModel.updateTimezone(it); showAccountSettings = false },
                    onBack = { showAccountSettings = false },
                    onExportBackup = { exportLauncher.launch("planner-backup.json") },
                    onExportLogs = {
                        diagnosticExportLauncher.launch("planner-diagnostico-${java.time.LocalDate.now()}.zip")
                    },
                )
            } else {
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
                    onOpenSettings = { showAccountSettings = true },
                )
            }
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
