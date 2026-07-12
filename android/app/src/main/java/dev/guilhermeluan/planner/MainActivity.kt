package dev.guilhermeluan.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.guilhermeluan.planner.session.LoginScreen
import dev.guilhermeluan.planner.session.LoginUiState
import dev.guilhermeluan.planner.session.PasswordChangeScreen
import dev.guilhermeluan.planner.session.PasswordChangeUiState
import dev.guilhermeluan.planner.session.PlannerViewModel
import dev.guilhermeluan.planner.session.SessionState
import dev.guilhermeluan.planner.session.SetupScreen
import dev.guilhermeluan.planner.session.AccountSettingsScreen
import dev.guilhermeluan.planner.day.DayScreen
import dev.guilhermeluan.planner.day.DayViewModel
import dev.guilhermeluan.planner.ui.components.PlannerPrimaryButton
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import java.net.URI

class MainActivity : ComponentActivity() {
    private val viewModel: PlannerViewModel by viewModels {
        PlannerViewModel.Factory(application as PlannerApplication)
    }
    private val dayViewModel: DayViewModel by viewModels {
        DayViewModel.Factory(application as PlannerApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlannerTheme {
                PlannerApp(viewModel, dayViewModel)
            }
        }
    }
}

@Composable
private fun PlannerApp(viewModel: PlannerViewModel, dayViewModel: DayViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dayState by dayViewModel.uiState.collectAsStateWithLifecycle()
    var showAccountSettings by remember { mutableStateOf(false) }
    val configuration = state.configuration
    if (configuration == null) {
        SetupScreen(onConfigured = viewModel::configure)
        return
    }
    if (state.isLoading && state.session == null) {
        LoadingScreen()
        return
    }
    when (val session = state.session ?: SessionState.SignedOut) {
        SessionState.SignedOut, is SessionState.Error -> LoginScreen(
            serverName = runCatching { URI(configuration.baseUrl).host }.getOrNull()
                ?: configuration.baseUrl,
            state = LoginUiState(isLoading = state.isLoading, error = state.error),
            onLogin = viewModel::login,
            onChangeServer = viewModel::changeServer,
        )
        is SessionState.PasswordChangeRequired -> PasswordChangeScreen(
            username = session.account.username,
            state = PasswordChangeUiState(isLoading = state.isLoading, error = state.error),
            onChangePassword = viewModel::changePassword,
        )
        is SessionState.Ready -> {
            LaunchedEffect(session.account.id, session.planner.id) {
                dayViewModel.bind(session)
            }
            if (showAccountSettings) {
                AccountSettingsScreen(
                    currentTimezone = session.account.timezone,
                    onSaveTimezone = {
                        viewModel.updateTimezone(it)
                        showAccountSettings = false
                    },
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
                    onLogout = {
                        showAccountSettings = false
                        dayViewModel.unbind()
                        viewModel.logout()
                    },
                    onOpenSettings = { showAccountSettings = true },
                )
            }
        }
        is SessionState.Blocked -> BlockedScreen(session.reason, viewModel::resumeAfterBlock)
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

@Composable
private fun ReadyScreen(session: SessionState.Ready, onLogout: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Seu Planner chegou.", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${session.account.username} · ${session.account.timezone}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PlannerPrimaryButton(text = "Abrir Hoje", onClick = {})
                    TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Sair desta Conta")
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedScreen(reason: String, onChangeServer: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Acesso bloqueado", style = MaterialTheme.typography.headlineMedium)
            Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onChangeServer) { Text("Entrar novamente") }
        }
    }
}
