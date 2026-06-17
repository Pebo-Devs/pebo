package app.pebo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pebo.core.NoteFilter

@Composable
fun Sidebar(vm: NotesViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        SidebarItem(
            label = "New Note",
            icon = Icons.Filled.Add,
            selected = false,
            onClick = { vm.createNote() },
        )
        Spacer(Modifier.height(8.dp))

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
            Spacer(Modifier.height(14.dp))
            Text(
                "TAGS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
            )
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
        SidebarItem(
            label = "Settings",
            icon = Icons.Filled.Settings,
            selected = vm.showSettings,
            onClick = { vm.openSettings() },
        )
    }
}
