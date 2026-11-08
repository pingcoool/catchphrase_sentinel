package com.audiokit.catchphrase.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Mint = Color(0xFF3DDC97)
val Amber = Color(0xFFFBBF24)
val Ink = Color(0xFF0A0E13)
val SurfaceDark = Color(0xFF111823)
val SurfaceVariantDark = Color(0xFF1A2430)
val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B98A5)
val OutlineDark = Color(0xFF2A3644)
val Danger = Color(0xFFF87171)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF04150E),
    primaryContainer = Color(0xFF123B2A),
    onPrimaryContainer = Color(0xFFB9F5D9),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF03201B),
    background = Ink,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDark,
    error = Danger,
    onError = Color(0xFF2A0A0A),
    tertiary = Amber
)

@Composable
fun CatchphraseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
