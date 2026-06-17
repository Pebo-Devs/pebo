package app.pebo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (note == null) {
            WelcomeWorkspace(onCreate = { vm.createNote() })
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }

            Text(
                if (vm.saving) "Saving…" else "Saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
            Spacer(Modifier.weight(1f))
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

        val state = rememberRichTextState()
        LaunchedEffect(note.id) {
            state.setMarkdown(note.body)
            snapshotFlow { state.annotatedString }
                .drop(1)
                .collect { vm.updateBody(note.id, state.toMarkdown()) }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, end = 28.dp, bottom = 22.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            tonalElevation = 1.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (note.body.isEmpty() && !note.trashed) {
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 30.dp)) {
                        Text(
                            "Untitled",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Start writing. The first line becomes the title. Use #tags anywhere.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        )
                    }
                }
                BasicRichTextEditor(
                    state = state,
                    readOnly = note.trashed,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 26.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun WelcomeWorkspace(onCreate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 44.dp, vertical = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                InfoPill("Local-first | Markdown | Cloud-ready")
                Text(
                    "Pebo",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "A focused notes workspace for ideas that belong to you.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WelcomeFeature("Write", "Fast Markdown notes with autosave.", Modifier.weight(1f))
                    WelcomeFeature("Organize", "Tags appear naturally as you type.", Modifier.weight(1f))
                    WelcomeFeature("Own", "Files stay portable in your storage.", Modifier.weight(1f))
                }
                Button(onClick = onCreate) {
                    Text("Create your first note")
                }
            }
        }
    }
}

@Composable
private fun WelcomeFeature(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
