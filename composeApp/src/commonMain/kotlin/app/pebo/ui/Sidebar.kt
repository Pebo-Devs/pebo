package app.pebo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pebo.core.NoteFilter

@Composable
fun Sidebar(vm: NotesViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 3.dp,
            ) {
                Text(
                    "P",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(start = 12.dp, top = 5.dp),
                )
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Pebo", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Markdown notes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = { vm.createNote() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("New note", modifier = Modifier.padding(start = 8.dp))
        }

        SectionLabel("LIBRARY", modifier = Modifier.padding(start = 12.dp, bottom = 4.dp, top = 4.dp))

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

        val tags = vm.tags
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("TAGS", modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(tags, key = { it.name }) { entry ->
                    val current = vm.filter
                    SidebarItem(
                        label = entry.name.substringAfterLast('/'),
                        icon = Icons.Filled.Tag,
                        selected = current is NoteFilter.Tag && current.name == entry.name,
                        onClick = { vm.selectFilter(NoteFilter.Tag(entry.name)) },
                        count = entry.count,
                        indentLevel = entry.name.count { it == '/' },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Storage",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Local workspace",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "Cloud sign-in foundation is ready for OneDrive and Google Drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        SidebarItem(
            label = "Settings",
            icon = Icons.Filled.Settings,
            selected = vm.showSettings,
            onClick = { vm.openSettings() },
        )
    }
}
