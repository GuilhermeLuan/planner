package dev.guilhermeluan.planner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Céu, morros e tintas da tela de alarme; os claros são os do Figma 07, os escuros apagam o céu para a noite. */
internal data class AlarmTones(
    val skyTop: Color,
    val skyBottom: Color,
    /** Os três morros do fundo, do mais distante ao mais próximo. */
    val hills: List<Color>,
    val ink: Color,
    val softInk: Color,
    /** Fundo do botão Tomei e do disco da pílula. */
    val button: Color,
    /** Texto do botão Tomei e a pílula. */
    val buttonInk: Color,
) {
    companion object {
        val Light = AlarmTones(
            skyTop = Color(0xFF2A1020),
            skyBottom = Color(0xFF9C3F6C),
            hills = listOf(Color(0xD96B2850), Color(0xE69C3F6C), Color(0x8C7A2E5E)),
            ink = Color(0xFFFFF4F8),
            softInk = Color(0xFFF6C9DA),
            button = Color(0xFFFFF4F8),
            buttonInk = Color(0xFFA83262),
        )
        val Dark = AlarmTones(
            skyTop = Color(0xFF12070D),
            skyBottom = Color(0xFF3E1B30),
            hills = listOf(Color(0xD9341426), Color(0xE64A1E38), Color(0x8C3A172C)),
            ink = Color(0xFFFFE8F0),
            softInk = Color(0xFFD8B8C5),
            button = Color(0xFFFF8FBA),
            buttonInk = Color(0xFF4A0B24),
        )
    }
}

internal val alarmTones: AlarmTones
    @Composable @ReadOnlyComposable get() =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) AlarmTones.Dark else AlarmTones.Light
