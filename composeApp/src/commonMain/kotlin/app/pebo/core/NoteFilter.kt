package app.pebo.core

/** What the note list is currently showing. */
sealed interface NoteFilter {
    data object All : NoteFilter
    data object Untagged : NoteFilter
    data object Trash : NoteFilter
    data class Tag(val name: String) : NoteFilter
}
