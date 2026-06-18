package app.pebo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

        val tagRows = vm.tagRows
        if (tagRows.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("Tags", modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(tagRows, key = { it.name }) { row ->
                    val current = vm.filter
                    TagTreeItem(
                        row = row,
                        selected = current is NoteFilter.Tag && current.name == row.name,
                        onClick = { vm.selectFilter(NoteFilter.Tag(row.name)) },
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

/**
 * One tag in the sidebar tree. Roots look like a normal [SidebarItem]; nested tags get a [TreeRail]
 * so the parent/child relationship reads at a glance, mirroring the note-list hierarchy.
 */
@Composable
private fun TagTreeItem(row: TagRow, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val target = when {
        selected -> scheme.primary.copy(alpha = 0.14f)
        hovered -> scheme.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(target, label = "tagBg")
    val railColor = scheme.onSurfaceVariant.copy(alpha = if (selected || hovered) 0.66f else 0.5f)
    val tint = if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.82f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 10.dp, end = 10.dp)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.depth > 0) {
            TreeRail(
                guides = row.guides,
                color = railColor,
                connectorY = 18.dp,
                modifier = Modifier.fillMaxHeight(),
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.Filled.Tag,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            row.leaf,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.count > 0) {
            CountPill(row.count)
        }
    }
}
