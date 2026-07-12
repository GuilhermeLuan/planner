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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.unit.dp
import dev.guilhermeluan.planner.ui.components.PlannerPrimaryButton

@Composable
fun OnboardingScreen(
    timezone: String,
    isSaving: Boolean,
    error: String?,
    onCreatePlanner: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                    Icon(Icons.Outlined.Person, null, Modifier.padding(14.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("Seu Planner começa aqui.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tudo fica neste dispositivo e continua disponível sem internet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth().testTag("person-name"),
                            label = { Text("Seu nome") },
                            supportingText = { Text(error ?: "Fuso sugerido: $timezone") },
                            isError = error != null,
                            enabled = !isSaving,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        PlannerPrimaryButton(
                            text = if (isSaving) "Criando…" else "Criar meu Planner",
                            onClick = { onCreatePlanner(name) },
                            enabled = !isSaving,
                        )
                    }
                }
            }
        }
    }
}
