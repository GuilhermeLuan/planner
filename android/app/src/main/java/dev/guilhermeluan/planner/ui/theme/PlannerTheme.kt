package dev.guilhermeluan.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Porcelain = Color(0xFFFFF8FB)
private val PetalPaper = Color(0xFFFFF0F6)
private val BlushInset = Color(0xFFFFE2EE)
private val Raspberry = Color(0xFFA83262)
private val WineInk = Color(0xFF2B1821)
private val PlumText = Color(0xFF755566)
private val RoseLine = Color(0xFFE5C4D1)

private val NightPorcelain = Color(0xFF1B1116)
private val NightPaper = Color(0xFF271920)
private val NightBlush = Color(0xFF35212A)
private val PetalLight = Color(0xFFFF8FBA)
private val NightInk = Color(0xFFFFE8F0)
private val NightSecondary = Color(0xFFD8B8C5)
private val NightLine = Color(0xFF5F3D4B)

private val LightColors = lightColorScheme(
    primary = Raspberry,
    onPrimary = Color.White,
    primaryContainer = BlushInset,
    onPrimaryContainer = WineInk,
    background = Porcelain,
    onBackground = WineInk,
    surface = PetalPaper,
    onSurface = WineInk,
    surfaceVariant = BlushInset,
    onSurfaceVariant = PlumText,
    outline = RoseLine,
    error = Color(0xFF9F253F),
)

private val DarkColors = darkColorScheme(
    primary = PetalLight,
    onPrimary = Color(0xFF4A0B24),
    primaryContainer = NightBlush,
    onPrimaryContainer = NightInk,
    background = NightPorcelain,
    onBackground = NightInk,
    surface = NightPaper,
    onSurface = NightInk,
    surfaceVariant = NightBlush,
    onSurfaceVariant = NightSecondary,
    outline = NightLine,
    error = Color(0xFFFFB2BF),
)

private val PlannerTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun PlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PlannerTypography,
        content = content,
    )
}

