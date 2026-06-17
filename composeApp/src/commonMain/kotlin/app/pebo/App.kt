package app.pebo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.dp
import app.pebo.ui.Editor
import app.pebo.ui.NoteList
import app.pebo.ui.NotesViewModel
import app.pebo.ui.SettingsScreen
import app.pebo.ui.Sidebar
import app.pebo.ui.VPaneDivider
import app.pebo.ui.theme.PeboTheme
import kotlinx.coroutines.launch

@Composable
fun App(vm: NotesViewModel, dataDir: String) {
    PeboTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (vm.showSettings) {
                SettingsScreen(vm, dataDir, onBack = { vm.closeSettings() })
                return@Surface
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 840.dp) {
                    val visibleNotes = vm.visibleNotes
                    LaunchedEffect(visibleNotes.map { it.id }, vm.filter, vm.query) {
                        if (vm.selectedNote == null && visibleNotes.isNotEmpty()) {
                            vm.select(visibleNotes.first().id)
                        }
                    }
                    Row(Modifier.fillMaxSize()) {
                        Sidebar(vm, Modifier.width(252.dp))
                        VPaneDivider()
                        NoteList(vm, Modifier.width(368.dp))
                        VPaneDivider()
                        Editor(vm, Modifier.weight(1f))
                    }
                } else {
                    CompactLayout(vm)
                }
            }
        }
    }
}

@Composable
private fun CompactLayout(vm: NotesViewModel) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Sidebar(vm, Modifier.width(280.dp))
            }
        },
    ) {
        if (vm.selectedNote != null) {
            Editor(vm, onBack = { vm.select(null) })
        } else {
            NoteList(vm, onMenu = { scope.launch { drawer.open() } })
        }
    }
}
