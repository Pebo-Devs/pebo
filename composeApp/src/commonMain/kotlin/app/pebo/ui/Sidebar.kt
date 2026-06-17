package app.pebo.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pebo.core.NoteFilter

@Composable
fun Sidebar(vm: NotesViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                            ),
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(9.dp)
                        .background(
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
            }
            Column(Modifier.padding(start = 11.dp)) {
                Text(
                    "Pebo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Private markdown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PrimaryAction(
            label = "New note",
            onClick = { vm.createNote() },
            icon = Icons.Filled.Add,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 14.dp),
        )

        SectionLabel("Library", modifier = Modifier.padding(start = 10.dp, bottom = 6.dp, top = 2.dp))

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
            SectionLabel("Tags", modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
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
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 8.dp),
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    )
                    Text(
                        "Local files",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    "Portable .md workspace. Cloud accounts connect from Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
