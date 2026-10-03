package dev.guilhermeluan.planner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Tons dos blocos de constância e dos cartões; os claros são os do Figma, os escuros seguem os tokens noturnos. */
internal data class YouTones(
    val waterTile: Color,
    val waterInk: Color,
    val doseTile: Color,
    val doseInk: Color,
    /** Rótulo do resumo de Remédios: o âmbar do Figma no claro, o tom de dose no escuro. */
    val doseLabel: Color,
    val routineTile: Color,
    val routineInk: Color,
    val tileLabel: Color,
    val cardLine: Color,
    val rowDivider: Color,
) {
    companion object {
        val Light = YouTones(
            waterTile = Color(0xFFE4E9FB), waterInk = Color(0xFF3D4C93),
            doseTile = Color(0xFFFDE6DA), doseInk = Color(0xFF8A4526), doseLabel = Color(0xFFB5603A),
            routineTile = Color(0xFFFFE3EE), routineInk = Color(0xFFA83262),
            tileLabel = Color(0xFF5E4552),
            cardLine = Color(0xFFF6DCE6), rowDivider = Color(0xFFF6E4EB),
        )
        val Dark = YouTones(
            waterTile = Color(0xFF2B2740), waterInk = Color(0xFFC9D0F5),
            doseTile = Color(0xFF3A2620), doseInk = Color(0xFFFFB48A), doseLabel = Color(0xFFFFB48A),
            routineTile = Color(0xFF35212A), routineInk = Color(0xFFFF8FBA),
            tileLabel = Color(0xFFD8B8C5),
            cardLine = Color(0xFF5F3D4B), rowDivider = Color(0xFF4A3039),
        )
    }
}

internal val youTones: YouTones
    @Composable @ReadOnlyComposable get() =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) YouTones.Dark else YouTones.Light
