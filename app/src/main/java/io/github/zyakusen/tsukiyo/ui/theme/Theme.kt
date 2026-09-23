package io.github.zyakusen.tsukiyo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5A8C0),
    onPrimary = Color(0xFF3A1B26),
    primaryContainer = Color(0xFF53303C),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFB8A7F0),
    onSecondary = Color(0xFF2A1E52),
    secondaryContainer = Color(0xFF41346C),
    onSecondaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF14111A),
    onBackground = Color(0xFFE9E1EA),
    surface = Color(0xFF1B1722),
    onSurface = Color(0xFFE9E1EA),
    surfaceVariant = Color(0xFF2A2433),
    onSurfaceVariant = Color(0xFFCAC0CE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF463D4C)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB93D67),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E0020),
    secondary = Color(0xFF6C5CA8),
    onSecondary = Color.White,
    background = Color(0xFFFBF7FB),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun AsmrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
