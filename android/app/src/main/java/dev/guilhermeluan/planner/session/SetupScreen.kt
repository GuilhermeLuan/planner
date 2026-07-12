package dev.guilhermeluan.planner.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.guilhermeluan.planner.ui.components.PlannerPrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onConfigured: (ServerConfiguration) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rawUrl by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingHttpUrl by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.Center),
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text("Seu Planner, no seu lugar.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Conecte esta instalação uma vez. Depois, seus Dias continuam disponíveis mesmo sem internet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Onde está seu servidor?", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = rawUrl,
                            onValueChange = {
                                rawUrl = it
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth().testTag("server-url"),
                            label = { Text("URL do servidor") },
                            placeholder = { Text("https://planner.exemplo.com") },
                            supportingText = {
                                Text(error ?: "Aceitamos HTTPS e HTTP para redes locais.")
                            },
                            isError = error != null,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        Spacer(Modifier.height(16.dp))
                        PlannerPrimaryButton(
                            text = "Continuar",
                            onClick = {
                                runCatching { ServerConfiguration.parse(rawUrl) }
                                    .onSuccess { configuration ->
                                        if (configuration.requiresInsecureTransportConfirmation) {
                                            pendingHttpUrl = configuration.baseUrl
                                        } else {
                                            onConfigured(configuration)
                                        }
                                    }
                                    .onFailure { error = it.message ?: "Informe uma URL válida" }
                            },
                        )
                    }
                }
            }
        }
    }

    pendingHttpUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { pendingHttpUrl = null },
            title = { Text("Sua conexão não estará protegida") },
            text = { Text("Credenciais e dados podem ser lidos durante o trajeto em HTTP.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingHttpUrl = null
                        onConfigured(ServerConfiguration.parse(url))
                    },
                ) { Text("Entendi, usar HTTP") }
            },
            dismissButton = {
                TextButton(onClick = { pendingHttpUrl = null }) { Text("Voltar") }
            },
        )
    }
}
