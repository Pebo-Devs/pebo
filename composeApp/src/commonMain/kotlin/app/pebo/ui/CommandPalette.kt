package app.pebo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pebo.core.NoteFilter

private sealed interface PaletteItem {
    val label: String
    val hint: String
    val icon: ImageVector
    fun run()
}

private class ActionItem(
    override val label: String,
    override val hint: String,
    override val icon: ImageVector,
    private val action: () -> Unit,
) : PaletteItem {
    override fun run() = action()
}

private class NoteItem(
    override val label: String,
    override val hint: String,
    override val icon: ImageVector,
    private val action: () -> Unit,
) : PaletteItem {
    override fun run() = action()
}

/**
 * A Ctrl/Cmd+K command palette: fuzzy-ish search over quick actions and every note. Arrow keys move
 * the selection, Enter activates, Esc (handled by the host) closes. Rendered as a scrim + floating
 * card so it reads as a modern launcher overlaid on the workspace.
 */
@Composable
fun CommandPalette(vm: NotesViewModel, visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        var query by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(0) }
        val fieldFocus = remember { FocusRequester() }
        val listState = rememberLazyListState()

        val items = remember(query, vm.active, vm.trashed.size) { buildItems(vm, query, onDismiss) }
        if (selected >= items.size) selected = (items.size - 1).coerceAtLeast(0)

        LaunchedEffect(visible) {
            if (visible) {
                query = ""
                selected = 0
                fieldFocus.requestFocus()
            }
        }
        LaunchedEffect(selected) {
            if (selected in items.indices) listState.animateScrollToItem(selected)
        }

        fun activate() {
            items.getOrNull(selected)?.run()
        }

        // Center the card using the REAL window pixel size (immune to the Compose-Desktop HiDPI
        // layout-constraint inflation that makes a plain `TopCenter` drift right on maximized windows).
        val density = LocalDensity.current
        val windowInfo = LocalWindowInfo.current
        val paletteWidth = 620.dp
        val sideInset = with(density) {
            ((windowInfo.containerSize.width.toDp() - paletteWidth) / 2).coerceAtLeast(0.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.TopStart,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.96f),
                exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.96f),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceBright,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .offset(x = sideInset)
                        .padding(top = 132.dp)
                        .width(paletteWidth)
                        .shadow(40.dp, RoundedCornerShape(20.dp))
                        // Swallow clicks so they don't fall through to the scrim's dismiss.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> {
                                    if (items.isNotEmpty()) selected = (selected + 1) % items.size
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (items.isNotEmpty()) selected = (selected - 1 + items.size) % items.size
                                    true
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    activate(); true
                                }
                                else -> false
                            }
                        },
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it; selected = 0 },
                                singleLine = true,
                                modifier = Modifier.weight(1f).focusRequester(fieldFocus),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Normal,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) {
                                        Text(
                                            "Search notes or run a command…",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                    inner()
                                },
                            )
                            KbdHint("Esc")
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .size(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        )
                        if (items.isEmpty()) {
                            Text(
                                "No matches",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.heightIn(max = 380.dp).padding(8.dp),
                            ) {
                                itemsIndexed(items) { index, item ->
                                    PaletteRow(
                                        item = item,
                                        active = index == selected,
                                        onClick = { item.run() },
                                        onHover = { selected = index },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(
    item: PaletteItem,
    active: Boolean,
    onClick: () -> Unit,
    onHover: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) scheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (active) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (active) scheme.onSurface else scheme.onSurface.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.hint.isNotEmpty()) {
            Text(
                item.hint,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun KbdHint(text: String) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

private fun buildItems(vm: NotesViewModel, query: String, dismiss: () -> Unit): List<PaletteItem> {
    val q = query.trim()
    val actions = listOf(
        ActionItem("New note", "Create", Icons.Filled.Add) { vm.createNote(); dismiss() },
        ActionItem("All notes", "Filter", Icons.Filled.Description) { vm.selectFilter(NoteFilter.All); dismiss() },
        ActionItem("Untagged", "Filter", Icons.AutoMirrored.Filled.LabelOff) { vm.selectFilter(NoteFilter.Untagged); dismiss() },
        ActionItem("Trash", "Filter", Icons.Filled.Delete) { vm.selectFilter(NoteFilter.Trash); dismiss() },
        ActionItem("Settings", "Open", Icons.Filled.Settings) { vm.openSettings(); dismiss() },
    ).filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }

    val matchedNotes = vm.active.asSequence()
        .filter { n ->
            q.isEmpty() ||
                n.title.contains(q, ignoreCase = true) ||
                n.body.contains(q, ignoreCase = true) ||
                n.tags.any { it.contains(q, ignoreCase = true) }
        }
        .take(if (q.isEmpty()) 8 else 25)
        .map { n ->
            NoteItem(
                label = n.title.ifBlank { "Untitled" },
                hint = if (n.tags.isEmpty()) "" else "#${n.tags.first()}",
                icon = Icons.Filled.Article,
            ) {
                vm.selectFilter(NoteFilter.All)
                vm.select(n.id)
                dismiss()
            }
        }
        .toList()

    return actions + matchedNotes
}
