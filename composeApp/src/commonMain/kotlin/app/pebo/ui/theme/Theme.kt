package app.pebo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Applies the selected [palette] resolved through the chosen [mode]. Falls back to the default
 * Midnight palette in System mode when no selection has been made yet. Also installs Pebo's bundled
 * Inter type scale and publishes the monospace family via [LocalMonoFontFamily].
 */
@Composable
fun PeboTheme(
    palette: PeboPalette = Palettes.byId(Palettes.DEFAULT_ID),
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val mono = peboMonoFamily()
    MaterialTheme(
        colorScheme = palette.scheme(mode, systemDark),
        typography = rememberPeboTypography(),
    ) {
        CompositionLocalProvider(LocalMonoFontFamily provides mono, content = content)
    }
}

