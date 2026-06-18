package app.pebo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.flow.drop

@Composable
fun Editor(
    vm: NotesViewModel,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val note = vm.selectedNote
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceBright),
    ) {
        if (note == null) {
            WelcomeWorkspace(onCreate = { vm.createNote() })
            return@Column
        }

        val state = rememberRichTextState()
        val tagColor = MaterialTheme.colorScheme.primary
        var showTagDialog by remember(note.id) { mutableStateOf(false) }
        var showLinkDialog by remember(note.id) { mutableStateOf(false) }
        LaunchedEffect(note.id) {
            state.setMarkdown(note.body)
            state.restyleHashtags(tagColor)
            snapshotFlow { state.annotatedString.text }
                .drop(1)
                .collect {
                    vm.updateBody(note.id, state.toMarkdown())
                    state.restyleHashtags(tagColor)
                }
        }

        if (showTagDialog) {
            AddTagDialog(
                onDismiss = { showTagDialog = false },
                onAdd = { rawTag ->
                    vm.addTag(note.id, rawTag)?.let { updatedBody ->
                        state.setMarkdown(updatedBody)
                        state.restyleHashtags(tagColor)
                    }
                    showTagDialog = false
                },
            )
        }

        if (showLinkDialog) {
            AddLinkDialog(
                onDismiss = { showLinkDialog = false },
                onAdd = { url ->
                    state.addLinkToSelection(url)
                    showLinkDialog = false
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }

            Column(Modifier.weight(1f).padding(start = if (onBack == null) 0.dp else 6.dp)) {
                Text(
                    note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    SaveStatus(saving = vm.saving)
                    InfoPill("Markdown")
                    if (!note.trashed) {
                        PillAction("Add tag", onClick = { showTagDialog = true }, icon = Icons.Filled.Tag)
                        PillAction("Child note", onClick = { vm.createChildNote(note.id) }, icon = Icons.Filled.Add)
                    }
                }
            }
            if (note.trashed) {
                IconButton(onClick = { vm.restore(note.id) }) {
                    Icon(Icons.Filled.RestoreFromTrash, contentDescription = "Restore")
                }
                IconButton(onClick = { vm.purge(note.id) }) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = "Delete forever")
                }
            } else {
                IconButton(onClick = { vm.togglePin(note.id) }) {
                    Icon(
                        if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin",
                        tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.trash(note.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Move to trash")
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )

        if (note.trashed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "In Trash — read only",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.restore(note.id) }) { Text("Restore") }
            }
        }

        if (!note.trashed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 10.dp),
            ) {
                EditorToolbar(
                    state = state,
                    onLink = { showLinkDialog = true },
                    modifier = Modifier
                        .widthIn(max = READING_WIDTH)
                        .fillMaxWidth(),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = READING_WIDTH)
                    .fillMaxWidth(),
            ) {
                if (note.body.isEmpty() && !note.trashed) {
                    Column(Modifier.padding(top = 34.dp)) {
                        Text(
                            "Untitled",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Start writing. Use #tags anywhere.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        )
                    }
                }
                BasicRichTextEditor(
                    state = state,
                    readOnly = note.trashed,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 30.dp, bottom = 64.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

private val READING_WIDTH = 740.dp

@Composable
private fun SaveStatus(saving: Boolean) {
    val dot by androidx.compose.animation.animateColorAsState(
        if (saving) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        label = "saveDot",
    )
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(dot, RoundedCornerShape(50)))
            Spacer(Modifier.width(7.dp))
            Text(
                if (saving) "Saving" else "Saved",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AddTagDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var tag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Tags are written into the note as #tag, including nested tags like #project/pebo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    singleLine = true,
                    label = { Text("Tag name") },
                    prefix = { Text("#") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = tag.isNotBlank(),
                onClick = { onAdd(tag) },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AddLinkDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Select text in the note first, then paste a URL. The selected text becomes the link.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("URL") },
                    placeholder = { Text("https://") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { onAdd(url.trim()) },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private val hashtagRegex = Regex("""(?<=^|\s)#[A-Za-z0-9_][A-Za-z0-9_/-]*""")

/**
 * Colours every `#tag` occurrence so tags read like pills inside the editor. The span is colour-only:
 * colour has no Markdown representation, so [RichTextState.toMarkdown] drops it and the saved file
 * keeps clean `#tag` text. Touches spans only (never the text), so it never retriggers the text
 * observer that drives autosave.
 */
private fun RichTextState.restyleHashtags(color: Color) {
    val text = annotatedString.text
    if (text.isEmpty()) return
    val style = SpanStyle(color = color)
    val saved = selection
    removeSpanStyle(style, TextRange(0, text.length))
    for (match in hashtagRegex.findAll(text)) {
        addSpanStyle(style, TextRange(match.range.first, match.range.last + 1))
    }
    selection = saved
}

@Composable
private fun WelcomeWorkspace(onCreate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f)),
            modifier = Modifier.fillMaxWidth(0.76f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 38.dp, vertical = 38.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AccentBar(width = 48.dp)
                Text(
                    "No note selected",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Choose a note from the list or start a clean page.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WelcomeFeature("Write", "Inline Markdown with autosave.", Modifier.weight(1f))
                    WelcomeFeature("Tag", "Organize with natural #tags.", Modifier.weight(1f))
                    WelcomeFeature("Own", "Portable local .md files.", Modifier.weight(1f))
                }
                PrimaryAction(label = "Create note", onClick = onCreate)
            }
        }
    }
}

@Composable
private fun WelcomeFeature(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
