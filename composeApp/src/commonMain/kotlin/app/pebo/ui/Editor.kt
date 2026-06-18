package app.pebo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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

        // The editing buffer holds the raw Markdown source; it is reset only when the note changes.
        var value by remember(note.id) {
            mutableStateOf(TextFieldValue(note.body, TextRange(note.body.length)))
        }
        var showTagDialog by remember(note.id) { mutableStateOf(false) }
        var showLinkDialog by remember(note.id) { mutableStateOf(false) }
        var showImageDialog by remember(note.id) { mutableStateOf(false) }
        var mode by remember(note.id) { mutableStateOf(EditorMode.Write) }

        fun applyEdit(newValue: TextFieldValue) {
            value = newValue
            vm.updateBody(note.id, newValue.text)
        }

        val tagColor = MaterialTheme.colorScheme.primary
        val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        val codeText = MaterialTheme.colorScheme.onSurface
        val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant
        val transformation = remember(tagColor, markerColor, codeText, codeBg, quoteColor) {
            MarkdownVisualTransformation(
                tag = tagColor,
                marker = markerColor,
                codeText = codeText,
                codeBg = codeBg,
                quote = quoteColor,
                link = tagColor,
            )
        }

        if (showTagDialog) {
            AddTagDialog(
                onDismiss = { showTagDialog = false },
                onAdd = { rawTag ->
                    vm.addTag(note.id, rawTag)?.let { updatedBody ->
                        value = TextFieldValue(updatedBody, TextRange(updatedBody.length))
                    }
                    showTagDialog = false
                },
            )
        }

        if (showLinkDialog) {
            AddLinkDialog(
                onDismiss = { showLinkDialog = false },
                onAdd = { url ->
                    applyEdit(MarkdownEditing.insertLink(value, url))
                    showLinkDialog = false
                },
            )
        }

        if (showImageDialog) {
            AddImageDialog(
                onDismiss = { showImageDialog = false },
                onAdd = { url, alt ->
                    applyEdit(MarkdownEditing.insertImage(value, url, alt))
                    showImageDialog = false
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(end = 250.dp),
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
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 5.dp),
                    ) {
                        SaveStatus(saving = vm.saving)
                        InfoPill("Markdown")
                        if (!note.trashed) {
                            PillAction("Add tag", onClick = { showTagDialog = true }, icon = Icons.Filled.Tag)
                            PillAction("Child note", onClick = { vm.createChildNote(note.id) }, icon = Icons.Filled.Add)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterEnd),
            ) {
                WritePreviewToggle(
                    mode = mode,
                    onChange = { mode = it },
                    modifier = Modifier.padding(end = 8.dp),
                )
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

        if (!note.trashed && mode == EditorMode.Write) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 10.dp),
            ) {
                EditorToolbar(
                    value = value,
                    onApply = ::applyEdit,
                    onLink = { showLinkDialog = true },
                    onImage = { showImageDialog = true },
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
                if (mode == EditorMode.Preview) {
                    MarkdownPreview(
                        text = value.text,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 30.dp, bottom = 64.dp),
                    )
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = { applyEdit(it) },
                        readOnly = note.trashed,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 30.dp, bottom = 64.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = transformation,
                        decorationBox = { inner ->
                            if (value.text.isEmpty() && !note.trashed) {
                                Column {
                                    Text(
                                        "Untitled",
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Start writing. Use #tags, code blocks, tables — anything Markdown.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                                    )
                                }
                            }
                            inner()
                        },
                    )
                }
            }
        }
    }
}

private val READING_WIDTH = 740.dp

internal enum class EditorMode { Write, Preview }

@Composable
private fun WritePreviewToggle(
    mode: EditorMode,
    onChange: (EditorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier.height(34.dp),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleSegment("Write", Icons.Filled.Edit, mode == EditorMode.Write) { onChange(EditorMode.Write) }
            ToggleSegment("Preview", Icons.Filled.Visibility, mode == EditorMode.Preview) { onChange(EditorMode.Preview) }
        }
    }
}

@Composable
private fun ToggleSegment(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun SaveStatus(saving: Boolean) {
    val dot by animateColorAsState(
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
                    "Select text first to use it as the link label, then paste a URL.",
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

@Composable
private fun AddImageDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Inserts ![alt](url). Use a web URL or a path to a local image file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Image URL or path") },
                    placeholder = { Text("https://") },
                )
                OutlinedTextField(
                    value = alt,
                    onValueChange = { alt = it },
                    singleLine = true,
                    label = { Text("Alt text (optional)") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { onAdd(url.trim(), alt.trim().ifBlank { "image" }) },
            ) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
