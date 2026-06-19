package app.pebo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pebo.platform.pickFolder
import app.pebo.ui.theme.PeboPalette
import app.pebo.ui.theme.Palettes
import app.pebo.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String, val icon: ImageVector) {
    Appearance("Appearance", Icons.Filled.Palette),
    Storage("Storage", Icons.Filled.Cloud),
    General("General", Icons.Filled.Tune),
    About("About", Icons.Filled.Info),
}

@Composable
fun SettingsScreen(
    vm: NotesViewModel,
    dataDir: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var section by remember { mutableStateOf(SettingsSection.Appearance) }
    val scheme = MaterialTheme.colorScheme

    Row(modifier.fillMaxSize().background(scheme.background)) {
        // ── Left nav ────────────────────────────────────────────────────────
        Column(
            Modifier
                .width(248.dp)
                .fillMaxHeight()
                .background(scheme.surface.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Back to notes", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.height(18.dp))
            SettingsSection.entries.forEach { entry ->
                NavRow(entry, selected = section == entry, onClick = { section = entry })
                Spacer(Modifier.height(2.dp))
            }
        }

        VPaneDivider()

        // ── Content ─────────────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 40.dp, vertical = 34.dp),
            ) {
                Column(Modifier.widthIn(max = 920.dp).fillMaxWidth()) {
                    when (section) {
                        SettingsSection.Appearance -> AppearancePanel(vm)
                        SettingsSection.Storage -> StoragePanel(vm, dataDir)
                        SettingsSection.General -> GeneralPanel()
                        SettingsSection.About -> AboutPanel()
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRow(section: SettingsSection, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            section.icon,
            contentDescription = null,
            tint = if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            section.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PanelHeader(title: String, subtitle: String) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
    }
}

// ── Appearance ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePanel(vm: NotesViewModel) {
    val scheme = MaterialTheme.colorScheme
    val systemDark = isSystemInDarkTheme()
    var search by remember { mutableStateOf("") }

    PanelHeader(
        "Appearance",
        "Pick a mode and a theme. ${Palettes.all.size} curated themes, each with a light and dark variant.",
    )

    SettingsCard {
        Text("Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeOption("System", Icons.Filled.Brightness6, vm.themeMode == ThemeMode.System) { vm.updateThemeMode(ThemeMode.System) }
            ModeOption("Light", Icons.Filled.LightMode, vm.themeMode == ThemeMode.Light) { vm.updateThemeMode(ThemeMode.Light) }
            ModeOption("Dark", Icons.Filled.DarkMode, vm.themeMode == ThemeMode.Dark) { vm.updateThemeMode(ThemeMode.Dark) }
        }
    }

    Spacer(Modifier.height(18.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Themes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.weight(1f))
        SearchField(search, onChange = { search = it }, modifier = Modifier.width(240.dp))
    }
    Spacer(Modifier.height(14.dp))

    val q = search.trim().lowercase()
    Palettes.groups.forEach { group ->
        val items = Palettes.all.filter {
            it.group == group && (q.isEmpty() || it.name.lowercase().contains(q) || it.group.lowercase().contains(q))
        }
        if (items.isEmpty()) return@forEach
        Text(
            group.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        ) {
            items.forEach { palette ->
                ThemeCard(
                    palette = palette,
                    preview = palette.scheme(vm.themeMode, systemDark),
                    active = vm.paletteId == palette.id,
                    onClick = { vm.selectPalette(palette.id) },
                )
            }
        }
    }
}

@Composable
private fun ModeOption(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (selected) scheme.primary.copy(alpha = 0.7f) else scheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        )
    }
}

/** A mini app mock painted in the palette's own resolved colors, so the card previews the theme. */
@Composable
private fun ThemeCard(
    palette: PeboPalette,
    preview: ColorScheme,
    active: Boolean,
    onClick: () -> Unit,
) {
    val outer = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(176.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(outer.surfaceVariant.copy(alpha = 0.4f))
            .border(
                if (active) 2.dp else 1.dp,
                if (active) outer.primary else outer.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(preview.background),
        ) {
            Column(
                Modifier.width(34.dp).fillMaxHeight().background(preview.surface).padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(preview.primary))
                Bar(preview.onSurfaceVariant, 0.9f)
                Bar(preview.onSurfaceVariant, 0.6f)
                Bar(preview.onSurfaceVariant, 0.75f)
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Bar(preview.onSurface, 0.7f, 5.dp)
                Bar(preview.onSurfaceVariant, 1f)
                Bar(preview.onSurfaceVariant, 0.85f)
                Spacer(Modifier.height(1.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(preview.primary.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Box(Modifier.size(width = 14.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(preview.primary))
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(preview.secondary.copy(alpha = 0.20f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Box(Modifier.size(width = 10.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(preview.secondary))
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                palette.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = outer.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (active) {
                Box(
                    Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(outer.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Active", tint = outer.onPrimary, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun Bar(color: Color, fraction: Float, height: Dp = 4.dp) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.55f)),
    )
}

// ── Storage ─────────────────────────────────────────────────────────────────

@Composable
private fun StoragePanel(vm: NotesViewModel, dataDir: String) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    PanelHeader("Storage", "Choose where your notes live. They are always portable .md files you fully own.")

    SettingsCard {
        StorageProvider.entries.forEachIndexed { index, provider ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            StorageRow(
                provider = provider,
                selected = vm.storageProvider == provider,
                onSelect = { vm.selectStorage(provider) },
            )
        }
    }

    Spacer(Modifier.height(18.dp))
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Notes folder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Text(
                    prettyNotesLocation(vm.notesDir.ifBlank { dataDir }),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(scheme.primary.copy(alpha = 0.12f))
                    .border(1.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                    .clickable {
                        scope.launch {
                            val picked = pickFolder("Choose your Pebo notes folder", vm.notesDir.ifBlank { dataDir })
                            if (!picked.isNullOrBlank()) vm.changeNotesDir(picked)
                        }
                    }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
            ) {
                Text("Change…", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = scheme.primary)
            }
        }
        Spacer(Modifier.height(11.dp))
        Text(
            "Pick any folder on this device — your notes/ and .trash/ live inside it as portable .md files you fully own. Switching folders reloads the workspace instantly.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.82f),
        )
    }
}

@Composable
private fun StorageRow(
    provider: StorageProvider,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) scheme.primary.copy(alpha = 0.5f) else scheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp),
            )
            .clickable(enabled = provider.available, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(50))
                .background(if (selected) scheme.primary else Color.Transparent)
                .border(if (selected) 0.dp else 1.5.dp, scheme.outline.copy(alpha = 0.7f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                provider.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (provider.available) scheme.onSurface else scheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(2.dp))
            Text(provider.description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        StatusBadge(provider.statusLabel, active = provider.available)
    }
}

@Composable
private fun StatusBadge(label: String, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (active) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.7f)
    val fg = if (active) scheme.primary else scheme.onSurfaceVariant
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/**
 * Renders the notes-folder handle for display. Real filesystem paths (desktop, and Android's default
 * folder) are shown verbatim. Android Storage Access Framework tree URIs — what
 * `content://…/tree/primary%3ADocuments%2FPebo` looks like raw — are decoded into a readable folder
 * label such as `Documents/Pebo`, so the card reads the same as the desktop path it mirrors.
 */
private fun prettyNotesLocation(raw: String): String {
    if (!raw.startsWith("content://")) return raw
    val marker = "/tree/"
    val start = raw.indexOf(marker)
    if (start < 0) return raw
    var encodedId = raw.substring(start + marker.length)
    val nextSegment = encodedId.indexOf('/') // tree URIs may append a /document/… part
    if (nextSegment >= 0) encodedId = encodedId.substring(0, nextSegment)
    val decoded = percentDecode(encodedId)
    return decoded.substringAfter(':', decoded).ifBlank { decoded }
}

/** Minimal percent-decoder for SAF document ids (folder labels are effectively ASCII). */
private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val sb = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val code = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (code != null) {
                sb.append(code.toChar())
                i += 3
                continue
            }
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}

// ── General / About ─────────────────────────────────────────────────────────

@Composable
private fun GeneralPanel() {
    PanelHeader("General", "App behavior and editing preferences.")
    SettingsCard {
        Text(
            "More options are on the way — autosave cadence, default new-note tags, startup view, and keyboard shortcuts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutPanel() {
    val scheme = MaterialTheme.colorScheme
    PanelHeader("About", "Pebo — a markdown-first, markdown-first notes app.")
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                imageVector = peboLogo(),
                contentDescription = "Pebo logo",
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Pebo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                Text("Personal Edit Board Online", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = scheme.primary)
                Text("Version 0.2-dev", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Native, offline-first notes built with Kotlin Multiplatform + Compose. Your notes stay as portable .md files in your own storage — local today, your cloud account next.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

// ── Small shared pieces ─────────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Search themes…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = scheme.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
