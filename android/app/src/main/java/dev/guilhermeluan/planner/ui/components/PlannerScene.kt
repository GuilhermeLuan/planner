package dev.guilhermeluan.planner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.guilhermeluan.planner.ui.navigation.PlannerTab

enum class SceneOrb { Moon, Sun, None }

data class SceneColors(
    val skyTop: Color,
    val skyBottom: Color,
    val orb: Color,
    val orbKind: SceneOrb,
    val hillBack: Color,
    val hillMid: Color,
    val hillFront: Color,
    val stars: Boolean,
) {
    companion object {
        fun forTab(tab: PlannerTab): SceneColors = when (tab) {
            PlannerTab.Today -> Today
            PlannerTab.Medicines -> Peach
            PlannerTab.Water -> Lavender
            PlannerTab.You -> Account
        }

        val Today = SceneColors(
            skyTop = Color(0xFF3E1B30), skyBottom = Color(0xFF7D4468),
            orb = Color(0xFFFFE3EE), orbKind = SceneOrb.Moon,
            hillBack = Color(0xFF7A2E5E), hillMid = Color(0xFF9E3B6E), hillFront = Color(0xFFC96592),
            stars = true,
        )
        val Peach = SceneColors(
            skyTop = Color(0xFF5A2646), skyBottom = Color(0xFFA66A78),
            orb = Color(0xFFFFD2B8), orbKind = SceneOrb.Sun,
            hillBack = Color(0xFFB5566F), hillMid = Color(0xFFD4707F), hillFront = Color(0xFFE8959A),
            stars = false,
        )
        val Lavender = SceneColors(
            skyTop = Color(0xFF3E1B30), skyBottom = Color(0xFF8A5A94),
            orb = Color(0xFFF3E9FF), orbKind = SceneOrb.Moon,
            hillBack = Color(0xFF6B4A92), hillMid = Color(0xFF8A5CA8), hillFront = Color(0xFFBC8CC8),
            stars = true,
        )
        val Account = Today.copy(orbKind = SceneOrb.None)
    }
}

private val StarPositions = listOf(
    0.27f to 0.14f, 0.58f to 0.12f, 0.12f to 0.28f, 0.50f to 0.30f, 0.83f to 0.20f, 0.07f to 0.58f,
)

/** Cena ilustrada de topo: céu em gradiente, astro, estrelas e três colinas em camadas. */
@Composable
fun PlannerScene(
    colors: SceneColors,
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
) {
    Canvas(modifier.fillMaxWidth().height(height).clipToBounds().testTag("planner-scene")) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(colors.skyTop, colors.skyBottom)))
        if (colors.stars) {
            StarPositions.forEach { (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 1.2.dp.toPx(), center = Offset(w * x, h * y))
            }
        }
        if (colors.orbKind != SceneOrb.None) {
            val center = Offset(w * if (colors.orbKind == SceneOrb.Moon) 0.87f else 0.82f, h * 0.30f)
            if (colors.orbKind == SceneOrb.Moon) {
                drawCircle(colors.orb.copy(alpha = 0.22f), radius = 30.dp.toPx(), center = center)
            }
            drawCircle(colors.orb, radius = 17.dp.toPx(), center = center)
        }
        drawOval(colors.hillBack, Offset(-w * 0.18f, h * 0.40f), Size(w * 0.9f, h * 1.2f))
        drawOval(colors.hillMid, Offset(w * 0.55f, h * 0.50f), Size(w * 0.9f, h * 1.2f))
        drawOval(colors.hillFront, Offset(-w * 0.25f, h * 0.72f), Size(w * 1.5f, h * 1.2f))
    }
}
