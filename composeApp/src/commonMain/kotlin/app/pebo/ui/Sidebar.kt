package app.pebo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.pebo.core.NoteFilter

@Composable
fun Sidebar(vm: NotesViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        var styleDialogTag by remember { mutableStateOf<String?>(null) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                imageVector = peboLogo(),
                contentDescription = "Pebo logo",
                modifier = Modifier.size(30.dp),
            )
            Column(Modifier.padding(start = 11.dp)) {
                Text(
                    "Pebo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Personal Editable Brain Organizer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PrimaryAction(
            label = "New note",
            onClick = { vm.createNote() },
            icon = Icons.Filled.Add,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 14.dp),
        )

        SectionLabel("Library", modifier = Modifier.padding(start = 10.dp, bottom = 6.dp, top = 2.dp))

        SidebarItem(
            label = "All Notes",
            icon = Icons.Filled.Description,
            selected = vm.filter == NoteFilter.All,
            onClick = { vm.selectFilter(NoteFilter.All) },
            count = vm.active.size,
        )
        SidebarItem(
            label = "Untagged",
            icon = Icons.AutoMirrored.Filled.LabelOff,
            selected = vm.filter == NoteFilter.Untagged,
            onClick = { vm.selectFilter(NoteFilter.Untagged) },
            count = vm.untaggedCount,
        )
        SidebarItem(
            label = "Trash",
            icon = Icons.Filled.Delete,
            selected = vm.filter == NoteFilter.Trash,
            onClick = { vm.selectFilter(NoteFilter.Trash) },
            count = vm.trashed.size,
        )

        val pinnedRows = vm.pinnedTagRows
        if (pinnedRows.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("Pinned", modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
            pinnedRows.forEach { row ->
                val current = vm.filter
                TagTreeItem(
                    row = row,
                    style = vm.tagStyle(row.name),
                    selected = current is NoteFilter.Tag && current.name == row.name,
                    onClick = { vm.selectFilter(NoteFilter.Tag(row.name)) },
                    onTogglePin = { vm.toggleTagPinned(row.name) },
                    onCustomize = { styleDialogTag = row.name },
                    onReset = { vm.resetTagStyle(row.name) },
                )
            }
        }

        val tagRows = vm.tagRows
        if (tagRows.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("Tags", modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(tagRows, key = { it.name }) { row ->
                    val current = vm.filter
                    TagTreeItem(
                        row = row,
                        style = vm.tagStyle(row.name),
                        selected = current is NoteFilter.Tag && current.name == row.name,
                        onClick = { vm.selectFilter(NoteFilter.Tag(row.name)) },
                        onTogglePin = { vm.toggleTagPinned(row.name) },
                        onCustomize = { styleDialogTag = row.name },
                        onReset = { vm.resetTagStyle(row.name) },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 8.dp),
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    )
                    Text(
                        "Local files",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    "Portable .md workspace. Cloud accounts connect from Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SidebarItem(
            label = "Settings",
            icon = Icons.Filled.Settings,
            selected = vm.showSettings,
            onClick = { vm.openSettings() },
        )

        styleDialogTag?.let { name ->
            TagStyleDialog(vm = vm, tagName = name, onDismiss = { styleDialogTag = null })
        }
    }
}

/**
 * One tag in the sidebar tree. Roots look like a normal [SidebarItem]; nested tags get a [TreeRail]
 * so the parent/child relationship reads at a glance, mirroring the note-list hierarchy. A [style]
 * may give the tag a custom icon and accent color so it "pops"; right-click (or the hover ⋯ button)
 * opens a context menu to pin, customise, or reset it.
 */
@Composable
private fun TagTreeItem(
    row: TagRow,
    style: TagStyle,
    selected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onCustomize: () -> Unit,
    onReset: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(Offset.Zero) }
    val target = when {
        selected -> scheme.primary.copy(alpha = 0.14f)
        hovered -> scheme.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(target, label = "tagBg")
    val railColor = scheme.onSurfaceVariant.copy(alpha = if (selected || hovered) 0.66f else 0.5f)
    val accent = style.colorArgb?.let { Color(it) }
    val tint = accent ?: if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.82f)
    val glyph = tagIconById(style.iconId) ?: Icons.Filled.Tag
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerInput(row.name) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            menuOffset = event.changes.first().position
                            menuOpen = true
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .padding(start = 10.dp, end = 6.dp)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.depth > 0) {
            TreeRail(
                guides = row.guides,
                color = railColor,
                connectorY = 18.dp,
                modifier = Modifier.fillMaxHeight(),
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            glyph,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            row.leaf,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hovered) {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = "Tag options",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            }
        } else if (row.count > 0) {
            CountPill(row.count)
        }

        val dpOffset = with(LocalDensity.current) { DpOffset(menuOffset.x.toDp(), menuOffset.y.toDp()) }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            offset = dpOffset,
            shape = RoundedCornerShape(13.dp),
            containerColor = scheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.7f)),
            shadowElevation = 16.dp,
            modifier = Modifier.width(220.dp),
        ) {
            TagMenuRow(
                icon = Icons.Filled.PushPin,
                label = if (style.pinned) "Unpin tag" else "Pin tag",
                onClick = { onTogglePin(); menuOpen = false },
            )
            TagMenuRow(
                icon = Icons.Filled.Palette,
                label = "Customize style…",
                onClick = { onCustomize(); menuOpen = false },
            )
            if (!style.isDefault) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                )
                TagMenuRow(
                    icon = Icons.Filled.RestartAlt,
                    label = "Reset style",
                    onClick = { onReset(); menuOpen = false },
                )
            }
        }
    }
}

@Composable
private fun TagMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface) },
        onClick = onClick,
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 2.dp),
    )
}

/**
 * The tag-style editor: pin toggle, a row of accent swatches, and a searchable grid over the full
 * [TAG_ICONS] catalogue. Every choice applies live through the view model (and persists), so there
 * is no separate save step — "Done" just closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagStyleDialog(vm: NotesViewModel, tagName: String, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val style = vm.tagStyle(tagName)
    val accent = style.colorArgb?.let { Color(it) } ?: scheme.primary
    var query by remember { mutableStateOf("") }
    val results = remember(query) { matchTagIconIds(TAG_ICON_IDS, query) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = scheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.width(560.dp),
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tagIconById(style.iconId) ?: Icons.Filled.Tag,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            "Tag style",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                        )
                        Text(
                            "#$tagName",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilterChip(
                        selected = style.pinned,
                        onClick = { vm.toggleTagPinned(tagName) },
                        label = { Text(if (style.pinned) "Pinned" else "Pin") },
                        leadingIcon = {
                            Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = scheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("Accent color", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    NoneSwatch(selected = style.colorArgb == null, onClick = { vm.setTagColor(tagName, null) })
                    TAG_COLORS.forEach { c ->
                        ColorSwatch(
                            color = Color(c),
                            selected = style.colorArgb == c,
                            onClick = { vm.setTagColor(tagName, c) },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Icon",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.setTagIconId(tagName, null) }, enabled = style.iconId != null) {
                        Text("Use default")
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search ${TAG_ICON_IDS.size} icons…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(46.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 240.dp),
                ) {
                    gridItems(results, key = { it }) { id ->
                        IconCell(
                            id = id,
                            selected = style.iconId == id,
                            accent = accent,
                            onClick = { vm.setTagIconId(tagName, id) },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.resetTagStyle(tagName) }, enabled = !style.isDefault) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.onSurface else scheme.outlineVariant.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NoneSwatch(selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.6f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "No accent",
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun IconCell(id: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val image = tagIconById(id) ?: Icons.Filled.Tag
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else scheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else scheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            image,
            contentDescription = id,
            tint = if (selected) accent else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
