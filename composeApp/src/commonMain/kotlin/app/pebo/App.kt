package app.pebo

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.pebo.ui.CommandPalette
import app.pebo.ui.Editor
import app.pebo.ui.NoteList
import app.pebo.ui.NotesViewModel
import app.pebo.ui.SettingsScreen
import app.pebo.ui.Sidebar
import app.pebo.ui.VPaneDivider
import app.pebo.ui.theme.PeboTheme
import app.pebo.ui.theme.Palettes
import kotlinx.coroutines.launch

@Composable
fun App(vm: NotesViewModel, dataDir: String) {
    PeboTheme(palette = Palettes.byId(vm.paletteId), mode = vm.themeMode) {
        var paletteOpen by remember { mutableStateOf(false) }
        val rootFocus = remember { FocusRequester() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        (e.isCtrlPressed || e.isMetaPressed) && e.key == Key.K -> {
                            paletteOpen = !paletteOpen
                            true
                        }
                        (e.isCtrlPressed || e.isMetaPressed) && e.isShiftPressed && e.key == Key.F -> {
                            vm.toggleFocusMode()
                            true
                        }
                        e.key == Key.Escape && paletteOpen -> {
                            paletteOpen = false
                            true
                        }
                        e.key == Key.Escape && vm.focusMode -> {
                            vm.exitFocusMode()
                            true
                        }
                        else -> false
                    }
                },
        ) {
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
                            if (!vm.focusMode) {
                                Sidebar(vm, Modifier.width(252.dp))
                                VPaneDivider()
                                NoteList(vm, Modifier.width(368.dp))
                                VPaneDivider()
                            }
                            if (vm.focusMode) {
                                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                    Editor(vm, Modifier.fillMaxHeight().widthIn(max = 920.dp).fillMaxWidth())
                                }
                            } else {
                                Editor(vm, Modifier.weight(1f))
                            }
                        }
                    } else {
                        CompactLayout(vm)
                    }
                }
            }

            CommandPalette(vm, visible = paletteOpen, onDismiss = { paletteOpen = false })
        }

        LaunchedEffect(Unit) { rootFocus.requestFocus() }
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
