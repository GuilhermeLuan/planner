package dev.guilhermeluan.planner

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import dev.guilhermeluan.planner.session.SessionState
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
    ) { /* notificação funciona sem a permissão, só perde o alerta */ }
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
    when (val current = state) {
        PlannerAppUiState.Loading -> LoadingScreen()
        is PlannerAppUiState.NeedsOnboarding -> OnboardingScreen(
            timezone = current.suggestedTimezone,
            isSaving = current.isSaving,
            error = current.error,
            onCreatePlanner = viewModel::createPlanner,
        )
        is PlannerAppUiState.Ready -> {
            val session = SessionState.Ready(current.localPlanner.account, current.localPlanner.planner)
            LaunchedEffect(session.account.id, session.planner.id) { dayViewModel.bind(session) }
            if (showAccountSettings) {
                AccountSettingsScreen(
                    currentName = session.account.username,
                    currentTimezone = session.account.timezone,
                    onSaveName = { viewModel.saveName(it) },
                    onSaveTimezone = { viewModel.saveTimezone(it); dayViewModel.updateTimezone(it); showAccountSettings = false },
                    onBack = { showAccountSettings = false },
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
