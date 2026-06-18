package app.pebo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()
private val PeboTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.0).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 30.sp, letterSpacing = 0.1.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 22.sp),
    bodySmall = Base.bodySmall.copy(lineHeight = 19.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
)

/**
 * Applies the selected [palette] resolved through the chosen [mode]. Falls back to the default
 * Midnight palette in System mode when no selection has been made yet.
 */
@Composable
fun PeboTheme(
    palette: PeboPalette = Palettes.byId(Palettes.DEFAULT_ID),
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = palette.scheme(mode, systemDark),
        typography = PeboTypography,
        content = content,
    )
}

