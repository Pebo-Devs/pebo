package app.pebo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.pebo.resources.Res
import app.pebo.resources.inter_bold
import app.pebo.resources.inter_extrabold
import app.pebo.resources.inter_medium
import app.pebo.resources.inter_regular
import app.pebo.resources.inter_semibold
import app.pebo.resources.jetbrainsmono_bold
import app.pebo.resources.jetbrainsmono_medium
import app.pebo.resources.jetbrainsmono_regular
import org.jetbrains.compose.resources.Font

/**
 * Pebo's signature sans — Inter — bundled so every platform renders identical, refined letterforms
 * instead of falling back to a generic system font. This is what gives the UI its premium feel.
 */
@Composable
fun peboSansFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
    Font(Res.font.inter_extrabold, FontWeight.ExtraBold),
)

/** JetBrains Mono for inline code, code blocks and any monospaced affordance. */
@Composable
fun peboMonoFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(Res.font.jetbrainsmono_medium, FontWeight.Medium),
    Font(Res.font.jetbrainsmono_bold, FontWeight.Bold),
)

/** Lets non-theme composables (e.g. the Markdown highlighter) reach the bundled monospace family. */
val LocalMonoFontFamily = compositionLocalOf<FontFamily> { FontFamily.Monospace }

/**
 * Builds the Pebo type scale on top of the Inter family. Tuned weights and negative tracking on the
 * display/title tiers give headings a tight, modern presence; body tiers get comfortable line-height
 * for long-form reading.
 */
@Composable
fun rememberPeboTypography(): Typography {
    val sans = peboSansFamily()
    val b = Typography()
    return remember(sans) {
        Typography(
            displayLarge = b.displayLarge.copy(fontFamily = sans, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp),
            displayMedium = b.displayMedium.copy(fontFamily = sans, fontWeight = FontWeight.Bold, letterSpacing = (-1.0).sp),
            displaySmall = b.displaySmall.copy(fontFamily = sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
            headlineLarge = b.headlineLarge.copy(fontFamily = sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
            headlineMedium = b.headlineMedium.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
            headlineSmall = b.headlineSmall.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            titleLarge = b.titleLarge.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
            titleMedium = b.titleMedium.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
            titleSmall = b.titleSmall.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
            bodyLarge = b.bodyLarge.copy(fontFamily = sans, fontSize = 16.5.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
            bodyMedium = b.bodyMedium.copy(fontFamily = sans, lineHeight = 22.sp, letterSpacing = 0.sp),
            bodySmall = b.bodySmall.copy(fontFamily = sans, lineHeight = 19.sp, letterSpacing = 0.05.sp),
            labelLarge = b.labelLarge.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
            labelMedium = b.labelMedium.copy(fontFamily = sans, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
            labelSmall = b.labelSmall.copy(fontFamily = sans, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
        )
    }
}
