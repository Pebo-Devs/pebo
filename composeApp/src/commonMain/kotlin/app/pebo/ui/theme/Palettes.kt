package app.pebo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** How the app resolves between a palette's light and dark variant. */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * A named theme. Every palette ships a full Material 3 [dark] and [light] scheme generated from a few
 * seed colors so the whole collection feels consistent. [defaultDark] decides which variant a preview
 * card shows when the app mode is "System".
 */
data class PeboPalette(
    val id: String,
    val name: String,
    val group: String,
    val accent: Color,
    val accent2: Color,
    val dark: ColorScheme,
    val light: ColorScheme,
    val defaultDark: Boolean,
) {
    fun scheme(mode: ThemeMode, systemDark: Boolean): ColorScheme = when (mode) {
        ThemeMode.Dark -> dark
        ThemeMode.Light -> light
        ThemeMode.System -> if (systemDark) dark else light
    }
}

private fun onOf(c: Color): Color =
    if (c.luminance() > 0.5f) Color(0xFF101216) else Color(0xFFFFFFFF)

/** Build a tasteful dark M3 scheme from a deep [base] background plus two accents. */
private fun darkScheme(accent: Color, accent2: Color, base: Color): ColorScheme {
    val up = Color.White
    fun l(t: Float) = lerp(base, up, t)
    return darkColorScheme(
        primary = accent,
        onPrimary = onOf(accent),
        primaryContainer = lerp(base, accent, 0.32f),
        onPrimaryContainer = lerp(up, accent, 0.25f),
        secondary = accent2,
        onSecondary = onOf(accent2),
        secondaryContainer = lerp(base, accent2, 0.30f),
        onSecondaryContainer = lerp(up, accent2, 0.25f),
        tertiary = accent2,
        onTertiary = onOf(accent2),
        background = base,
        onBackground = l(0.92f),
        surface = l(0.05f),
        onSurface = l(0.93f),
        surfaceDim = l(0.02f),
        surfaceBright = l(0.085f),
        surfaceContainerLowest = l(0.012f),
        surfaceContainerLow = l(0.04f),
        surfaceContainer = l(0.075f),
        surfaceContainerHigh = l(0.105f),
        surfaceContainerHighest = l(0.135f),
        surfaceVariant = l(0.11f),
        onSurfaceVariant = l(0.60f),
        outline = l(0.24f),
        outlineVariant = l(0.13f),
        surfaceTint = accent,
    )
}

/** Build a clean light M3 scheme from a near-white [base] plus two accents. */
private fun lightScheme(accent: Color, accent2: Color, base: Color): ColorScheme {
    val ink = Color(0xFF0C0E14)
    val white = Color.White
    fun d(t: Float) = lerp(base, ink, t)
    return lightColorScheme(
        primary = accent,
        onPrimary = onOf(accent),
        primaryContainer = lerp(base, accent, 0.20f),
        onPrimaryContainer = lerp(ink, accent, 0.30f),
        secondary = accent2,
        onSecondary = onOf(accent2),
        secondaryContainer = lerp(base, accent2, 0.18f),
        onSecondaryContainer = lerp(ink, accent2, 0.30f),
        tertiary = accent2,
        onTertiary = onOf(accent2),
        background = base,
        onBackground = d(0.90f),
        surface = lerp(base, white, 0.55f),
        onSurface = d(0.90f),
        surfaceDim = d(0.05f),
        surfaceBright = white,
        surfaceContainerLowest = white,
        surfaceContainerLow = lerp(base, white, 0.7f),
        surfaceContainer = lerp(base, white, 0.45f),
        surfaceContainerHigh = d(0.04f),
        surfaceContainerHighest = d(0.07f),
        surfaceVariant = d(0.05f),
        onSurfaceVariant = d(0.55f),
        outline = d(0.26f),
        outlineVariant = d(0.12f),
        surfaceTint = accent,
    )
}

private fun palette(
    id: String,
    name: String,
    group: String,
    accent: Color,
    accent2: Color,
    darkBase: Color,
    lightBase: Color,
    lightAccent: Color = accent,
    lightAccent2: Color = accent2,
    defaultDark: Boolean = true,
) = PeboPalette(
    id = id,
    name = name,
    group = group,
    accent = if (defaultDark) accent else lightAccent,
    accent2 = if (defaultDark) accent2 else lightAccent2,
    dark = darkScheme(accent, accent2, darkBase),
    light = lightScheme(lightAccent, lightAccent2, lightBase),
    defaultDark = defaultDark,
)

/** The curated theme collection (35+). Grouped for the Settings grid. */
object Palettes {
    const val DEFAULT_ID = "midnight"

    val all: List<PeboPalette> = listOf(
        // ── Signature / Dark ────────────────────────────────────────────────
        palette("midnight", "Midnight", "Signature", Color(0xFF6E8BFF), Color(0xFFB49BFF), Color(0xFF090A0F), Color(0xFFF4F5F9), lightAccent = Color(0xFF4E63D6), lightAccent2 = Color(0xFF7A5CD6)),
        palette("obsidian", "Obsidian", "Dark", Color(0xFF8AA0FF), Color(0xFF6FE3C2), Color(0xFF0B0D12), Color(0xFFF5F6FA), lightAccent = Color(0xFF3D5AFE)),
        palette("carbon", "Carbon", "Dark", Color(0xFFC4CAD8), Color(0xFF8A93A6), Color(0xFF0C0C0E), Color(0xFFF3F4F6), lightAccent = Color(0xFF4B5563)),
        palette("graphite", "Graphite", "Dark", Color(0xFF9DA8C0), Color(0xFF6E7891), Color(0xFF0E0F13), Color(0xFFF2F3F7), lightAccent = Color(0xFF515B72)),
        palette("slate", "Slate", "Dark", Color(0xFF7DD3FC), Color(0xFF94A3B8), Color(0xFF0B0F17), Color(0xFFF1F5F9), lightAccent = Color(0xFF0284C7)),

        // ── Editor classics ─────────────────────────────────────────────────
        palette("nord", "Nord", "Classics", Color(0xFF88C0D0), Color(0xFF81A1C1), Color(0xFF2E3440), Color(0xFFECEFF4), lightAccent = Color(0xFF5E81AC)),
        palette("dracula", "Dracula", "Classics", Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF282A36), Color(0xFFF8F8F2), lightAccent = Color(0xFF7C3AED), lightAccent2 = Color(0xFFDB2777)),
        palette("tokyo-night", "Tokyo Night", "Classics", Color(0xFF7AA2F7), Color(0xFFBB9AF7), Color(0xFF1A1B26), Color(0xFFEAEEF7), lightAccent = Color(0xFF3D5AFE)),
        palette("one-dark", "One Dark", "Classics", Color(0xFF61AFEF), Color(0xFFC678DD), Color(0xFF282C34), Color(0xFFEFF1F5), lightAccent = Color(0xFF2563EB)),
        palette("gruvbox", "Gruvbox", "Classics", Color(0xFFFABD2F), Color(0xFFFE8019), Color(0xFF282828), Color(0xFFFBF1C7), lightAccent = Color(0xFFB57614), lightAccent2 = Color(0xFFAF3A03), defaultDark = true),
        palette("solarized", "Solarized", "Classics", Color(0xFF268BD2), Color(0xFF2AA198), Color(0xFF002B36), Color(0xFFFDF6E3), lightAccent = Color(0xFF268BD2)),
        palette("monokai", "Monokai", "Classics", Color(0xFFA6E22E), Color(0xFFF92672), Color(0xFF272822), Color(0xFFF7F7EE), lightAccent = Color(0xFF66A60A), lightAccent2 = Color(0xFFD81B60)),
        palette("catppuccin", "Catppuccin", "Classics", Color(0xFFCBA6F7), Color(0xFF89B4FA), Color(0xFF1E1E2E), Color(0xFFEFF1F5), lightAccent = Color(0xFF8839EF), lightAccent2 = Color(0xFF1E66F5)),
        palette("rose-pine", "Rosé Pine", "Classics", Color(0xFFEBBCBA), Color(0xFFC4A7E7), Color(0xFF191724), Color(0xFFFAF4ED), lightAccent = Color(0xFFB4637A), lightAccent2 = Color(0xFF907AA9)),

        // ── Nature ──────────────────────────────────────────────────────────
        palette("forest", "Forest", "Nature", Color(0xFFA3BE8C), Color(0xFF8FBCBB), Color(0xFF11201A), Color(0xFFF1F7F1), lightAccent = Color(0xFF2E7D32)),
        palette("ocean", "Deep Ocean", "Nature", Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF0A1621), Color(0xFFEFF7FB), lightAccent = Color(0xFF0277BD)),
        palette("aurora", "Aurora", "Nature", Color(0xFF6EE7B7), Color(0xFF93C5FD), Color(0xFF0B1220), Color(0xFFF0FBF6), lightAccent = Color(0xFF059669), lightAccent2 = Color(0xFF2563EB)),
        palette("mint", "Mint", "Nature", Color(0xFF34D399), Color(0xFF22D3EE), Color(0xFF071411), Color(0xFFF1FAF6), lightAccent = Color(0xFF0E9F6E)),
        palette("sky", "Sky", "Nature", Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFF081420), Color(0xFFF0F9FF), lightAccent = Color(0xFF0EA5E9)),
        palette("emerald", "Emerald", "Nature", Color(0xFF10B981), Color(0xFF34D399), Color(0xFF07140F), Color(0xFFF0FAF4), lightAccent = Color(0xFF059669)),

        // ── Warm ────────────────────────────────────────────────────────────
        palette("ember", "Ember", "Warm", Color(0xFFFF8A3D), Color(0xFFFFB454), Color(0xFF17110C), Color(0xFFFBF3EC), lightAccent = Color(0xFFD2691E)),
        palette("crimson", "Crimson", "Warm", Color(0xFFFF6B6B), Color(0xFFFF9E80), Color(0xFF160B0D), Color(0xFFFBF0F0), lightAccent = Color(0xFFD32F2F)),
        palette("tangerine", "Tangerine", "Warm", Color(0xFFFF9F1C), Color(0xFFFFBF69), Color(0xFF16100A), Color(0xFFFDF6EC), lightAccent = Color(0xFFE8590C)),
        palette("sand", "Sand", "Warm", Color(0xFFD4A24E), Color(0xFFC98E6A), Color(0xFF14110B), Color(0xFFFAF6EE), lightAccent = Color(0xFFB4791F)),
        palette("ruby", "Ruby", "Warm", Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFF160A0E), Color(0xFFFCF0F2), lightAccent = Color(0xFFE11D48)),
        palette("rose-quartz", "Rose Quartz", "Warm", Color(0xFFF0A6C0), Color(0xFFE7A7CF), Color(0xFF1A1014), Color(0xFFFBF1F5), lightAccent = Color(0xFFD6789B), defaultDark = false),

        // ── Vibrant ─────────────────────────────────────────────────────────
        palette("grape", "Grape", "Vibrant", Color(0xFF9B5DE5), Color(0xFFF15BB5), Color(0xFF15101F), Color(0xFFF6F1FB), lightAccent = Color(0xFF7C3AED), lightAccent2 = Color(0xFFDB2777)),
        palette("sapphire", "Sapphire", "Vibrant", Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0xFF0A0F1E), Color(0xFFEFF4FF), lightAccent = Color(0xFF2563EB)),
        palette("synthwave", "Synthwave", "Vibrant", Color(0xFFFF2E97), Color(0xFF00E5FF), Color(0xFF1A0E2E), Color(0xFFF7F0FB), lightAccent = Color(0xFFD6217A), lightAccent2 = Color(0xFF0891B2)),
        palette("cyber-lime", "Cyber Lime", "Vibrant", Color(0xFFB6FF3C), Color(0xFF00E5A0), Color(0xFF0C1108), Color(0xFFF6FBEC), lightAccent = Color(0xFF558B2F), lightAccent2 = Color(0xFF00897B)),
        palette("coral", "Coral Reef", "Vibrant", Color(0xFFFF6F61), Color(0xFF2EC4B6), Color(0xFF0E1416), Color(0xFFFBF2F0), lightAccent = Color(0xFFE2503F), lightAccent2 = Color(0xFF0F9B8E)),
        palette("lavender", "Lavender", "Vibrant", Color(0xFFB39DFF), Color(0xFF80D8FF), Color(0xFF14111F), Color(0xFFF6F4FF), lightAccent = Color(0xFF7C5CFF), defaultDark = false),

        // ── Light-first ─────────────────────────────────────────────────────
        palette("paper", "Paper", "Light", Color(0xFF5B6CC9), Color(0xFF8C7BD8), Color(0xFF11131A), Color(0xFFF4F5F9), lightAccent = Color(0xFF4E63D6), lightAccent2 = Color(0xFF7A5CD6), defaultDark = false),
        palette("daylight", "Daylight", "Light", Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFF0E1116), Color(0xFFFFFFFF), lightAccent = Color(0xFF2563EB), defaultDark = false),
        palette("latte", "Latte", "Light", Color(0xFF8839EF), Color(0xFF1E66F5), Color(0xFF181825), Color(0xFFEFF1F5), lightAccent = Color(0xFF8839EF), lightAccent2 = Color(0xFF1E66F5), defaultDark = false),
        palette("sepia", "Sepia", "Light", Color(0xFFB4791F), Color(0xFFA8642A), Color(0xFF1A140C), Color(0xFFF6EFE0), lightAccent = Color(0xFF9A6312), defaultDark = false),
        palette("fog", "Fog", "Light", Color(0xFF5B7A99), Color(0xFF7E97AC), Color(0xFF12161B), Color(0xFFF2F5F8), lightAccent = Color(0xFF3F6080), defaultDark = false),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String?): PeboPalette = byId[id] ?: byId.getValue(DEFAULT_ID)

    val groups: List<String> = all.map { it.group }.distinct()
}
