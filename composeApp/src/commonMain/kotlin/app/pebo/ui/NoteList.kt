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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onMenu != null) {
                    IconButton(onClick = onMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        filterTitle(vm.filter),
                        style = MaterialTheme.typography.headlineSmall,
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
private fun NoteRow(row: NoteTreeRow, selected: Boolean, onClick: () -> Unit) {
    val note = row.note
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme

    val targetBg = when {
        selected -> scheme.surfaceContainer
        hovered -> scheme.surfaceContainer.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(targetBg, label = "rowBg")
    val borderAlpha by animateFloatAsState(if (selected) 0.8f else 0f, label = "rowBorder")
    val elevation by animateDpAsState(if (selected) 10.dp else 0.dp, label = "rowElev")
    val railColor = scheme.onSurfaceVariant.copy(alpha = if (selected || hovered) 0.68f else 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
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
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (row.depth > 0) {
                TreeRail(
                    guides = row.guides,
                    color = railColor,
                    modifier = Modifier.fillMaxHeight(),
                )
                Spacer(Modifier.width(8.dp))
            }
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
