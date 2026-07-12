package dev.guilhermeluan.planner.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.guilhermeluan.planner.ui.components.PlannerPrimaryButton

data class PasswordChangeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

@Composable
fun PasswordChangeScreen(
    username: String,
    state: PasswordChangeUiState,
    onChangePassword: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val matches = password.length >= 8 && password == confirmation

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.Center),
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.padding(14.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text("Crie sua senha.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "A senha temporária de $username termina aqui. Escolha uma senha com pelo menos 8 caracteres.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth().testTag("new-password"),
                            enabled = !state.isLoading,
                            label = { Text("Nova senha") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmation,
                            onValueChange = { confirmation = it },
                            modifier = Modifier.fillMaxWidth().testTag("confirm-password"),
                            enabled = !state.isLoading,
                            label = { Text("Confirmar nova senha") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            isError = confirmation.isNotEmpty() && password != confirmation,
                            supportingText = {
                                when {
                                    confirmation.isNotEmpty() && password != confirmation -> Text("As senhas precisam ser iguais.")
                                    password.isNotEmpty() && password.length < 8 -> Text("Use pelo menos 8 caracteres.")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        state.error?.let { error ->
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(error, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        PlannerPrimaryButton(
                            text = if (state.isLoading) "Salvando…" else "Salvar nova senha",
                            enabled = matches && !state.isLoading,
                            onClick = { onChangePassword(password) },
                        )
                    }
                }
            }
        }
    }
}
