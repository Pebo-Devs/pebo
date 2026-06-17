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

private val Accent = Color(0xFF2563EB)
private val AccentMuted = Color(0xFF5B6EE1)
private val Ink = Color(0xFF0F172A)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFFFFFFF),
    secondary = AccentMuted,
    background = Color(0xFFF7F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F3F8),
    outlineVariant = Color(0xFFDDE3EC),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF657084),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AA2FF),
    onPrimary = Color(0xFF081226),
    secondary = Color(0xFFA6B6FF),
    background = Color(0xFF0B0D12),
    surface = Color(0xFF11141B),
    surfaceVariant = Color(0xFF1A1F2A),
    outlineVariant = Color(0xFF273142),
    onSurface = Color(0xFFE8EDF7),
    onSurfaceVariant = Color(0xFFA6B0C2),
)

private val PeboTypography = Typography(
    displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.9).sp),
    headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35).sp),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Typography().titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 17.sp, lineHeight = 30.sp),
    bodyMedium = Typography().bodyMedium.copy(lineHeight = 22.sp),
)

@Composable
fun PeboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = PeboTypography,
        content = content,
    )
}
