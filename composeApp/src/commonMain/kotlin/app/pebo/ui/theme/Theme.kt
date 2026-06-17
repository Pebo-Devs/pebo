package app.pebo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Amber = Color(0xFFFFC857)
private val AmberDark = Color(0xFFE3A72F)
private val Ink = Color(0xFF161618)

private val LightColors = lightColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = AmberDark,
    background = Color(0xFFF7F4ED),
    surface = Color(0xFFFFFCF7),
    surfaceVariant = Color(0xFFF0E9DD),
    outlineVariant = Color(0xFFE2D8C9),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF6D665C),
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = AmberDark,
    background = Color(0xFF151517),
    surface = Color(0xFF1D1D20),
    surfaceVariant = Color(0xFF29282C),
    outlineVariant = Color(0xFF3B3940),
    onSurface = Color(0xFFF2EEF4),
    onSurfaceVariant = Color(0xFFC9C0CC),
)

private val PeboTypography = Typography(
    displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Typography().titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(lineHeight = 28.sp),
)

@Composable
fun PeboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = PeboTypography,
        content = content,
    )
}
