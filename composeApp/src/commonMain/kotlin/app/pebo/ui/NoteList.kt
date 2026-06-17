package app.pebo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(17.dp),
        color = if (selected) MaterialTheme.colorScheme.background.copy(alpha = 0.96f) else Color.Transparent,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.depth > 0) {
                Spacer(Modifier.width((row.depth * 18).dp))
                Box(
                    Modifier
                        .width(10.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f), RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(10.dp))
            }
            if (selected) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(46.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.hasChildren) {
                        Text(
                            "▾",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    if (note.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        note.title.ifBlank { "Untitled" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        DateLabel.short(note.modified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    )
                }
                val snippet = note.snippet
                if (snippet.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
