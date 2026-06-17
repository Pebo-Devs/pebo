package app.pebo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFE6A817)
private val AmberDark = Color(0xFFC8901A)

private val LightColors = lightColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1A1A),
    secondary = AmberDark,
    background = Color(0xFFFCFCFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1EFE9),
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1A1A),
    secondary = AmberDark,
    background = Color(0xFF1B1B1D),
    surface = Color(0xFF202022),
    surfaceVariant = Color(0xFF2A2A2D),
)

@Composable
fun PeboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
