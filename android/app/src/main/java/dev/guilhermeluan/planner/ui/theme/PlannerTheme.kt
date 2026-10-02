package dev.guilhermeluan.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.R

/** Tokens de cor do "crepúsculo rosa"; o tema escuro deriva dos tokens noturnos. */
data class PlannerPalette(
    val porcelain: Color,
    val surface: Color,
    val blush: Color,
    val raspberry: Color,
    val onRaspberry: Color,
    val wine: Color,
    val nightPlum: Color,
    val waterLavender: Color,
    val peach: Color,
    val alertAmber: Color,
    val secondaryInk: Color,
    val chipInk: Color,
    val mutedInk: Color,
    val line: Color,
    val error: Color,
) {
    companion object {
        val Light = PlannerPalette(
            porcelain = Color(0xFFFFF7FA),
            surface = Color.White,
            blush = Color(0xFFFFE3EE),
            raspberry = Color(0xFFA83262),
            onRaspberry = Color.White,
            wine = Color(0xFF2B1821),
            nightPlum = Color(0xFF3E1B30),
            waterLavender = Color(0xFF5868B0),
            peach = Color(0xFFFDE6DA),
            alertAmber = Color(0xFFC2410C),
            secondaryInk = Color(0xFF755566),
            chipInk = Color(0xFF7A4A60),
            mutedInk = Color(0xFF9C7A8B),
            line = Color(0xFFF0D3DF),
            error = Color(0xFF9F253F),
        )
        val Dark = PlannerPalette(
            porcelain = Color(0xFF1B1116),
            surface = Color(0xFF271920),
            blush = Color(0xFF35212A),
            raspberry = Color(0xFFFF8FBA),
            onRaspberry = Color(0xFF4A0B24),
            wine = Color(0xFFFFE8F0),
            nightPlum = Color(0xFF3E1B30),
            waterLavender = Color(0xFF8E9CE0),
            peach = Color(0xFF3A2620),
            alertAmber = Color(0xFFFF9A62),
            secondaryInk = Color(0xFFD8B8C5),
            chipInk = Color(0xFFF0C9DA),
            mutedInk = Color(0xFFB596A5),
            line = Color(0xFF5F3D4B),
            error = Color(0xFFFFB2BF),
        )
    }
}

private val LocalPalette = staticCompositionLocalOf { PlannerPalette.Light }

/** Acesso às cores do app que o Material não modela (pêssego, lavanda, âmbar, tinta suave). */
object PlannerExtras {
    val palette: PlannerPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

private fun PlannerPalette.toColorScheme(dark: Boolean) =
    if (dark) {
        darkColorScheme(
            primary = raspberry, onPrimary = onRaspberry,
            primaryContainer = blush, onPrimaryContainer = wine,
            background = porcelain, onBackground = wine,
            surface = surface, onSurface = wine,
            surfaceContainerLowest = surface,
            surfaceVariant = blush, onSurfaceVariant = secondaryInk,
            outline = line, error = error,
        )
    } else {
        lightColorScheme(
            primary = raspberry, onPrimary = onRaspberry,
            primaryContainer = blush, onPrimaryContainer = wine,
            background = porcelain, onBackground = wine,
            surface = porcelain, onSurface = wine,
            surfaceContainerLowest = surface,
            surfaceVariant = blush, onSurfaceVariant = secondaryInk,
            outline = line, error = error,
        )
    }

@OptIn(ExperimentalTextApi::class)
private val Fraunces = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).map { weight ->
        Font(
            R.font.fraunces,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("SOFT", 0f),
                FontVariation.Setting("WONK", 1f),
            ),
        )
    },
)

@OptIn(ExperimentalTextApi::class)
private val Nunito = FontFamily(
    listOf(FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { weight ->
        Font(
            R.font.nunito,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private val PlannerTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Normal, fontSize = 34.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Normal, fontSize = 28.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.8.sp),
)

@Composable
fun PlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) PlannerPalette.Dark else PlannerPalette.Light
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(darkTheme),
            typography = PlannerTypography,
            content = content,
        )
    }
}
