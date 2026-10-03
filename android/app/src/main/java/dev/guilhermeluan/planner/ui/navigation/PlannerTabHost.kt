package dev.guilhermeluan.planner.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background

@Composable
fun PlannerTabHost(
    today: @Composable (openTab: (PlannerTab) -> Unit) -> Unit,
    water: @Composable () -> Unit,
    you: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    medicines: (@Composable () -> Unit)? = null,
) {
    LightStatusBarIconsOnScene()
    var selected by rememberSaveable { mutableStateOf(PlannerTab.Today) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            when (selected) {
                PlannerTab.Today -> today { selected = it }
                PlannerTab.Medicines -> medicines?.invoke() ?: EmptyTabScreen(
                    tab = PlannerTab.Medicines,
                    title = "Nenhum remédio ainda",
                    message = "Cadastre um remédio para ser avisado na hora de cada dose.",
                )
                PlannerTab.Water -> water()
                PlannerTab.You -> you()
            }
        }
        PlannerTabBar(selected = selected, onSelect = { selected = it })
    }
}

/** Todas as abas abrem com uma cena escura no topo, então os ícones da barra de status ficam claros. */
@Composable
private fun LightStatusBarIconsOnScene() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}
