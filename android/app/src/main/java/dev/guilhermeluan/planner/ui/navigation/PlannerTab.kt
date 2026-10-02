package dev.guilhermeluan.planner.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

enum class PlannerTab(val label: String, val tag: String, val icon: ImageVector) {
    Today("Hoje", "hoje", Icons.Rounded.WbSunny),
    Medicines("Remédios", "remedios", CapsuleIcon),
    Water("Água", "agua", Icons.Rounded.WaterDrop),
    You("Você", "voce", Icons.Rounded.Person),
}

/** Cápsula na diagonal, como no ícone de Remédios do Figma. */
private val CapsuleIcon: ImageVector = ImageVector.Builder(
    name = "Capsule", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        // retângulo arredondado 20x8 girado 45° em torno de (12, 12)
        moveTo(18.7f, 5.3f)
        curveTo(17.1f, 3.7f, 14.6f, 3.7f, 13.0f, 5.3f)
        lineTo(5.3f, 13.0f)
        curveTo(3.7f, 14.6f, 3.7f, 17.1f, 5.3f, 18.7f)
        curveTo(6.9f, 20.3f, 9.4f, 20.3f, 11.0f, 18.7f)
        lineTo(18.7f, 11.0f)
        curveTo(20.3f, 9.4f, 20.3f, 6.9f, 18.7f, 5.3f)
        close()
    }
}.build()
