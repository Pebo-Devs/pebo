package app.pebo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pebo.core.Slug
import app.pebo.export.ExportFormat
import app.pebo.export.exportNote
import app.pebo.export.pickSaveFile
import app.pebo.ui.theme.LocalMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        var showExportMenu by remember(note.id) { mutableStateOf(false) }
        var showMoreMenu by remember(note.id) { mutableStateOf(false) }
        var exportStatus by remember(note.id) { mutableStateOf<String?>(null) }
        val exportScope = rememberCoroutineScope()

        // Tag IntelliSense: caret-anchored autocomplete over known tags.
        val writeScroll = rememberScrollState()
        var textLayout by remember(note.id) { mutableStateOf<TextLayoutResult?>(null) }
        var fieldOrigin by remember(note.id) { mutableStateOf(Offset.Zero) }
        var acIndex by remember(note.id) { mutableIntStateOf(0) }
        var acDismissed by remember(note.id) { mutableStateOf<String?>(null) }

        // Outline + heading folding ("Outline with focus").
        var outlineOpen by remember(note.id) { mutableStateOf(false) }
        var collapsed by remember(note.id) { mutableStateOf(emptySet<Int>()) }
        var scrollOrdinal by remember(note.id) { mutableStateOf<Int?>(null) }
        var activeOrdinal by remember(note.id) { mutableStateOf<Int?>(null) }
        var writeNavTick by remember(note.id) { mutableIntStateOf(0) }
        var writeNavTarget by remember(note.id) { mutableStateOf<Int?>(null) }
        val previewListState = remember(note.id) { LazyListState() }
        // Outline + word count are derived OFF the typing hot path. Re-parsing the whole document on
        // every keystroke inside composition was a major source of editor lag (worst on large notes).
        // A single snapshot-flow collector recomputes them a beat after typing pauses on a background
        // dispatcher — collectLatest cancels superseded work, giving a clean debounce without the
        // experimental Flow.debounce operator. Seeded once per note so both read correctly on open.
        var outline by remember(note.id) { mutableStateOf(parseOutline(value.text)) }
        var wordCount by remember(note.id) { mutableIntStateOf(countWords(value.text)) }
        LaunchedEffect(note.id) {
            snapshotFlow { value.text }.collectLatest { text ->
                delay(200)
                val parsed = withContext(Dispatchers.Default) { parseOutline(text) }
                outline = parsed
                wordCount = countWords(text)
            }
        }

        fun applyEdit(newValue: TextFieldValue) {
            value = newValue
            vm.updateBody(note.id, newValue.text)
        }

        // Multi-format export: native "Save As" dialog (off the UI thread), then render + write.
        fun runExport(format: ExportFormat) {
            val current = value.text
            val title = note.title
            exportScope.launch(Dispatchers.Default) {
                val defaultName = "${Slug.of(title).ifBlank { "note" }}.${format.ext}"
                val dest = pickSaveFile("Export note", defaultName, format.ext) ?: return@launch
                val ok = exportNote(format, title, current, dest, vm.notesDir.ifBlank { null })
                exportStatus = if (ok) "Exported ${format.ext.uppercase()}" else "Export failed"
            }
        }

        LaunchedEffect(exportStatus) {
            if (exportStatus != null) {
                delay(3500)
                exportStatus = null
            }
        }

        // Write-mode outline navigation: scroll the source field so the picked heading is near the top.
        LaunchedEffect(writeNavTick) {
            val off = writeNavTarget ?: return@LaunchedEffect
            val tl = textLayout ?: return@LaunchedEffect
            val rect = runCatching { tl.getCursorRect(off.coerceIn(0, value.text.length)) }.getOrNull()
                ?: return@LaunchedEffect
            writeScroll.animateScrollTo((rect.top - 24f).coerceAtLeast(0f).toInt())
        }

        fun navigateTo(item: OutlineItem) {
            activeOrdinal = item.ordinal
            if (mode == EditorMode.Preview) {
                collapsed = emptySet()
                scrollOrdinal = item.ordinal
            } else {
                val off = item.charOffset.coerceIn(0, value.text.length)
                value = value.copy(selection = TextRange(off))
                writeNavTarget = off
                writeNavTick++
            }
        }

        val tagColor = MaterialTheme.colorScheme.primary
        val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        val codeText = MaterialTheme.colorScheme.onSurface
        val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant
        val codeFont = LocalMonoFontFamily.current
        val transformation = remember(tagColor, markerColor, codeText, codeBg, quoteColor, codeFont) {
            MarkdownVisualTransformation(
                tag = tagColor,
                marker = markerColor,
                codeText = codeText,
                codeBg = codeBg,
                quote = quoteColor,
                link = tagColor,
                codeFont = codeFont,
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(2.dp))
            }

            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    SaveStatus(saving = vm.saving)
                    InfoPill(if (wordCount == 1) "1 word" else "$wordCount words")
                    exportStatus?.let { InfoPill(it) }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = { vm.toggleFocusMode() }) {
                    Icon(
                        if (vm.focusMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (vm.focusMode) "Exit focus mode" else "Focus mode",
                        tint = if (vm.focusMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { outlineOpen = !outlineOpen }) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = "Toggle outline",
                        tint = if (outlineOpen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(
                            Icons.Filled.IosShare,
                            contentDescription = "Export note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false },
                    ) {
                        ExportFormat.entries.forEach { fmt ->
                            DropdownMenuItem(
                                text = { Text(fmt.label) },
                                onClick = {
                                    showExportMenu = false
                                    runExport(fmt)
                                },
                            )
                        }
                    }
                }
                HeaderDivider()
                WritePreviewToggle(
                    mode = mode,
                    onChange = { mode = it },
                )
                HeaderDivider()
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        if (note.trashed) {
                            DropdownMenuItem(
                                text = { Text("Restore") },
                                leadingIcon = { Icon(Icons.Filled.RestoreFromTrash, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    vm.restore(note.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete forever") },
                                leadingIcon = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    vm.purge(note.id)
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Add tag") },
                                leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showTagDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add child note") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    vm.createChildNote(note.id)
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (note.pinned) "Unpin" else "Pin") },
                                leadingIcon = {
                                    Icon(
                                        if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = null,
                                        tint = if (note.pinned) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    vm.togglePin(note.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move to trash") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    vm.trash(note.id)
                                },
                            )
                        }
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

        val tagCandidates = vm.tagRows.map { TagCandidate(it.name, it.count) }
        val acQuery =
            if (!note.trashed && mode == EditorMode.Write && value.selection.collapsed) {
                tagCompletionContext(value.text, value.selection.start)
            } else null
        val acSuggestions = remember(acQuery, tagCandidates) {
            acQuery?.let { rankTagSuggestions(it.query, tagCandidates) } ?: emptyList()
        }
        LaunchedEffect(acQuery?.query) { acIndex = 0 }
        val acOpen = acSuggestions.isNotEmpty() && acQuery?.query != acDismissed

        fun acceptCompletion(cand: TagCandidate) {
            val q = acQuery ?: return
            val insert = "#" + cand.name
            val newText = value.text.substring(0, q.start) + insert + value.text.substring(q.end)
            applyEdit(TextFieldValue(newText, TextRange(q.start + insert.length)))
            acDismissed = null
        }

        Row(modifier = Modifier.fillMaxSize()) {
            if (outlineOpen) {
                OutlinePanel(
                    items = outline,
                    activeOrdinal = activeOrdinal,
                    onPick = { navigateTo(it) },
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
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
                        modifier = Modifier.fillMaxSize(),
                        listState = previewListState,
                        collapsed = collapsed,
                        onToggleFold = { idx ->
                            collapsed = if (idx in collapsed) collapsed - idx else collapsed + idx
                        },
                        contentPadding = PaddingValues(top = 30.dp, bottom = 64.dp),
                        scrollToOrdinal = scrollOrdinal,
                        onScrollConsumed = { scrollOrdinal = null },
                    )
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = { applyEdit(it) },
                        readOnly = note.trashed,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(writeScroll)
                            .padding(top = 30.dp, bottom = 64.dp)
                            .onGloballyPositioned { fieldOrigin = it.positionInWindow() }
                            .onPreviewKeyEvent { ev ->
                                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                // Tag autocomplete owns navigation keys while its popup is open.
                                if (acOpen) {
                                    when (ev.key) {
                                        Key.DirectionDown -> {
                                            acIndex = (acIndex + 1) % acSuggestions.size
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.DirectionUp -> {
                                            acIndex = (acIndex - 1 + acSuggestions.size) % acSuggestions.size
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.Enter, Key.NumPadEnter, Key.Tab -> {
                                            acceptCompletion(acSuggestions[acIndex.coerceIn(0, acSuggestions.lastIndex)])
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.Escape -> {
                                            acDismissed = acQuery?.query
                                            return@onPreviewKeyEvent true
                                        }
                                        else -> {}
                                    }
                                }
                                if (note.trashed) return@onPreviewKeyEvent false
                                // Smart editing: continue lists/quotes on Enter, indent with Tab.
                                when (ev.key) {
                                    Key.Enter, Key.NumPadEnter -> {
                                        val next = MarkdownEditing.smartEnter(value)
                                        if (next != null) {
                                            applyEdit(next)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    Key.Tab -> {
                                        val next = if (ev.isShiftPressed) {
                                            MarkdownEditing.outdentSelection(value)
                                        } else {
                                            MarkdownEditing.indentSelection(value)
                                        }
                                        applyEdit(next)
                                        true
                                    }
                                    else -> false
                                }
                            },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = transformation,
                        onTextLayout = { textLayout = it },
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

                    if (acOpen) {
                        val caretPos = value.selection.start
                        val rect = textLayout?.let { runCatching { it.getCursorRect(caretPos) }.getOrNull() }
                        if (rect != null) {
                            TagCompletionPopup(
                                anchorLeft = fieldOrigin.x + rect.left,
                                anchorTop = fieldOrigin.y + rect.top - writeScroll.value,
                                anchorBottom = fieldOrigin.y + rect.bottom - writeScroll.value,
                                suggestions = acSuggestions,
                                selectedIndex = acIndex.coerceIn(0, acSuggestions.lastIndex),
                                onPick = { acceptCompletion(it) },
                            )
                        }
                    }
                }
              }
            }
        }
    }
}

private val READING_WIDTH = 740.dp

internal enum class EditorMode { Write, Preview }

@Composable
private fun HeaderDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

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

private val wordSplitRegex = Regex("\\s+")

/** Word count used by the editor status pill; computed off the UI thread, debounced. */
internal fun countWords(text: String): Int =
    text.split(wordSplitRegex).count { it.isNotBlank() }
