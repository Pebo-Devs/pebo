package app.pebo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import app.pebo.core.DateLabel
import app.pebo.core.NoteFilter

@Composable
fun NoteList(
    vm: NotesViewModel,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null,
) {
    val notes = vm.visibleNotes
    val rows = vm.visibleNoteRows
    val isTrash = vm.filter == NoteFilter.Trash
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceDim),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onMenu != null) {
                    IconButton(onClick = onMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        filterTitle(vm.filter),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        noteCountLabel(notes.size, isTrash),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    onClick = { vm.createNote() },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "New note",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SearchField(
                value = vm.query,
                onValueChange = vm::updateQuery,
            )
        }

        if (notes.isEmpty()) {
            EmptyStateCard(
                title = if (isTrash) "Trash is empty" else "Start your library",
                message = if (isTrash) {
                    "Deleted notes will appear here before they are permanently removed."
                } else {
                    "Capture ideas, meeting notes, lists, and research in plain Markdown."
                },
                actionText = if (isTrash) null else "Create note",
                onAction = if (isTrash) null else ({ vm.createNote() }),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                items(rows, key = { it.note.id }) { row ->
                    NoteRow(
                        row = row,
                        selected = row.note.id == vm.selectedId,
                        inTrash = isTrash,
                        vm = vm,
                        onClick = { vm.select(row.note.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            "Search notes, text, or tags",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        )
                    }
                    inner()
                },
            )
        }
    }
}

private fun filterTitle(filter: NoteFilter): String =
    when (filter) {
        NoteFilter.All -> "All notes"
        NoteFilter.Untagged -> "Untagged"
        NoteFilter.Trash -> "Trash"
        is NoteFilter.Tag -> "#${filter.name}"
    }

private fun noteCountLabel(count: Int, trash: Boolean): String =
    when {
        count == 0 && trash -> "Nothing to restore"
        count == 0 -> "No notes yet"
        count == 1 -> "1 note"
        else -> "$count notes"
    }

@Composable
private fun NoteRow(
    row: NoteTreeRow,
    selected: Boolean,
    inTrash: Boolean,
    vm: NotesViewModel,
    onClick: () -> Unit,
) {
    val note = row.note
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(Offset.Zero) }

    val targetBg = when {
        selected -> scheme.surfaceContainer
        hovered -> scheme.surfaceContainer.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(targetBg, label = "rowBg")
    val borderAlpha by animateFloatAsState(if (selected) 0.8f else 0f, label = "rowBorder")
    val elevation by animateDpAsState(if (selected) 10.dp else 0.dp, label = "rowElev")
    val railColor = scheme.primary.copy(alpha = if (selected || hovered) 0.95f else 0.55f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerInput(note.id) {
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
            },
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
            if (row.depth > 0) {
                TreeRail(
                    guides = row.guides,
                    color = railColor,
                    modifier = Modifier.fillMaxHeight(),
                    connectorY = 22.dp,
                )
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .shadow(elevation, RoundedCornerShape(16.dp), clip = false)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .then(
                        if (borderAlpha > 0f) {
                            Modifier.border(
                                BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = borderAlpha)),
                                RoundedCornerShape(16.dp),
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .padding(top = 2.dp, end = 10.dp)
                            .width(3.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) scheme.primary else Color.Transparent),
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (note.pinned) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                note.title.ifBlank { "Untitled" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) scheme.onSurface else scheme.onSurface.copy(alpha = 0.92f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (row.hasChildren) {
                                Spacer(Modifier.width(8.dp))
                                SubnoteBadge(row.childCount)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                DateLabel.short(note.modified),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.78f),
                            )
                        }
                        val snippet = note.snippet
                        if (snippet.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (note.tags.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = chipSpacing) {
                                note.tags.take(4).forEach { TagChip(it) }
                            }
                        }
                    }
                }
            }
        }

        NoteRowContextMenu(
            expanded = menuOpen,
            offset = menuOffset,
            inTrash = inTrash,
            pinned = note.pinned,
            onDismiss = { menuOpen = false },
            onOpen = { onClick(); menuOpen = false },
            onTogglePin = { vm.togglePin(note.id); menuOpen = false },
            onAddChild = { vm.createChildNote(note.id); menuOpen = false },
            onTrash = { vm.trash(note.id); menuOpen = false },
            onRestore = { vm.restore(note.id); menuOpen = false },
            onPurge = { vm.purge(note.id); menuOpen = false },
        )
    }
}

@Composable
private fun NoteRowContextMenu(
    expanded: Boolean,
    offset: Offset,
    inTrash: Boolean,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onAddChild: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dpOffset = with(LocalDensity.current) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = dpOffset,
        shape = RoundedCornerShape(13.dp),
        containerColor = scheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.7f)),
        shadowElevation = 16.dp,
        modifier = Modifier.width(238.dp),
    ) {
        if (inTrash) {
            ContextMenuRow(Icons.Filled.RestoreFromTrash, "Restore note", onClick = onRestore)
            ContextMenuDivider()
            ContextMenuRow(Icons.Filled.DeleteForever, "Delete permanently", destructive = true, onClick = onPurge)
        } else {
            ContextMenuRow(Icons.Filled.OpenInFull, "Open note", onClick = onOpen)
            ContextMenuRow(Icons.Filled.PushPin, if (pinned) "Unpin from top" else "Pin to top", onClick = onTogglePin)
            ContextMenuRow(Icons.Filled.SubdirectoryArrowRight, "Add child note", onClick = onAddChild)
            ContextMenuDivider()
            ContextMenuRow(Icons.Filled.Delete, "Move to Trash", destructive = true, onClick = onTrash)
        }
    }
}

@Composable
private fun ContextMenuRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (destructive) scheme.error else scheme.onSurface
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium, color = fg) },
        onClick = onClick,
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = fg.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 2.dp),
    )
}

@Composable
private fun ContextMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * Draws the file-tree connector rails for a nested note. See [TreeRail] in Components.
 */
@Composable
private fun SubnoteBadge(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Icon(
            Icons.Filled.AccountTree,
            contentDescription = "Subnotes",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
