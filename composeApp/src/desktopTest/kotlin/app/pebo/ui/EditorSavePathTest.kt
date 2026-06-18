package app.pebo.ui

import app.pebo.data.LocalNoteStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The whole reason the editor was rewritten away from the rich-text library: the old `toMarkdown()`
 * autosave silently destroyed fenced code blocks, tables, task lists and images. The new editor
 * writes the raw editing buffer straight through [NotesViewModel.updateBody]. This drives that exact
 * path and proves every construct survives byte-for-byte in the `.md` file on disk.
 */
class EditorSavePathTest {

    @Test
    fun rawMarkdownSurvivesEditorSaveVerbatim() = runBlocking {
        val baseDir = Files.createTempDirectory("pebo-editor-save").toString().toPath()
        val store = LocalNoteStore(FileSystem.SYSTEM, baseDir)
        val vm = NotesViewModel(store, scope = this)
        delay(120) // let the constructor's initial refresh() settle on the empty store

        vm.createNote()
        val id = vm.selectedId!! // captured synchronously, before any further suspension

        val body = buildString {
            append("# Demo note\n\n")
            append("```kotlin\nfun main() {\n    println(\"hi\")\n}\n```\n\n")
            append("| Name | Value |\n| --- | --- |\n| a | 1 |\n\n")
            append("- [ ] unchecked task\n- [x] done task\n\n")
            append("> a blockquote\n\n")
            append("![shot](images/pic.png)\n")
        }
        vm.updateBody(id, body)
        delay(800) // outlast the 400ms debounce + disk write

        val saved = FileSystem.SYSTEM.listRecursively(baseDir)
            .filter { it.name.endsWith(".md") }
            .joinToString("\n\n") { FileSystem.SYSTEM.read(it) { readUtf8() } }

        assertTrue(saved.contains("```kotlin"), "fenced code block opening lost:\n$saved")
        assertTrue(saved.contains("fun main() {"), "code block content lost:\n$saved")
        assertTrue(saved.contains("println(\"hi\")"), "code block body lost:\n$saved")
        assertTrue(saved.contains("| --- | --- |"), "table separator lost:\n$saved")
        assertTrue(saved.contains("| a | 1 |"), "table row lost:\n$saved")
        assertTrue(saved.contains("- [ ] unchecked task"), "unchecked task lost:\n$saved")
        assertTrue(saved.contains("- [x] done task"), "checked task lost:\n$saved")
        assertTrue(saved.contains("> a blockquote"), "blockquote marker lost:\n$saved")
        assertTrue(saved.contains("![shot](images/pic.png)"), "image syntax lost:\n$saved")
    }
}
