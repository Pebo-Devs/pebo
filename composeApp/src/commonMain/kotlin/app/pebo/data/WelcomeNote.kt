package app.pebo.data

import app.pebo.core.Note
import okio.FileSystem
import okio.Path

/**
 * Seeds a small welcome note tree the first time Pebo runs against an empty workspace, so the
 * app is never an empty void on first launch. Shared by every platform entry point (desktop,
 * Android) so the first-run experience is identical. A `.welcome-note-created` marker in
 * [baseDir] makes this idempotent and keeps it from re-seeding an existing folder of notes.
 */
suspend fun seedWelcomeNoteIfNeeded(store: NoteStore, fs: FileSystem, baseDir: Path) {
    val marker = baseDir / ".welcome-note-created"
    if (fs.exists(marker)) return

    val snapshot = store.load()
    if (snapshot.active.isNotEmpty() || snapshot.trashed.isNotEmpty()) {
        fs.createDirectories(baseDir)
        fs.write(marker) { writeUtf8("existing-notes\n") }
        return
    }

    val welcome = Note.new(
        body = """
            # Welcome to Pebo

            Pebo is a focused Markdown workspace for notes that belong to you. #getting-started

            - Write in plain Markdown with live formatting and autosave.
            - Type #tags anywhere — try nested ones like #project/pebo.
            - Build a tree: open a note and choose "Add child note".
            - Everything stays as portable `.md` files you fully own.

            The notes below are a small tree so you can see how nesting looks.
        """.trimIndent(),
    ).copy(pinned = true)
    store.save(welcome)

    store.save(
        Note.new(
            parentId = welcome.id,
            body = """
                # Getting started

                A child note — the connector lines on the left tie it back to "Welcome to Pebo". #project/pebo

                1. Edit this text; it saves automatically.
                2. Use the toolbar for headings, bold, lists, and links.
                3. Add your own #tags to grow the sidebar tree.
            """.trimIndent(),
        ),
    )

    store.save(
        Note.new(
            parentId = welcome.id,
            body = """
                # Tips & shortcuts

                Another child of Welcome, one level deeper in the tag tree. #project/pebo/ui

                - Click a tag in the sidebar to filter notes.
                - Pin important notes to keep them on top.
                - Trash is non-destructive — restore anytime.
            """.trimIndent(),
        ),
    )

    store.save(
        Note.new(
            body = """
                # Ideas

                A separate top-level note with its own tag branch. #project/docs

                Capture anything here, then organize it with tags or child notes later.
            """.trimIndent(),
        ),
    )

    fs.write(marker) { writeUtf8("created\n") }
}
