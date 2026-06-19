package app.pebo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteFileTest {

    @Test
    fun serialize_then_parse_roundtrips() {
        val note = Note(
            id = "abc-123",
            body = "# Hello\nworld with #tag",
            created = "2026-06-17T10:00:00Z",
            modified = "2026-06-17T11:00:00Z",
            parentId = "parent-note",
            pinned = true,
        )
        val text = NoteFile.serialize(note)
        val parsed = NoteFile.parse(text, fallbackId = "wrong", trashed = false)

        assertEquals("abc-123", parsed.id)
        assertEquals("# Hello\nworld with #tag", parsed.body)
        assertEquals("2026-06-17T10:00:00Z", parsed.created)
        assertEquals("2026-06-17T11:00:00Z", parsed.modified)
        assertEquals("parent-note", parsed.parentId)
        assertTrue(parsed.pinned)
        assertEquals("Hello", parsed.title)
    }

    @Test
    fun pinned_omitted_when_false() {
        val note = Note("id", "body", "c", "m", pinned = false)
        assertFalse(NoteFile.serialize(note).contains("pinned"))
    }

    @Test
    fun parse_handles_crlf_and_missing_frontmatter() {
        val parsed = NoteFile.parse("just a plain body\r\nsecond line", fallbackId = "file-id", trashed = true)
        assertEquals("file-id", parsed.id)
        assertEquals("just a plain body\nsecond line", parsed.body)
        assertTrue(parsed.trashed)
    }

    @Test
    fun timestamps_with_colons_parse_correctly() {
        val text = "---\nid: x\ncreated: 2026-06-17T21:23:05.123Z\nmodified: 2026-06-17T21:24:00.000Z\n---\nBody"
        val parsed = NoteFile.parse(text, fallbackId = "f", trashed = false)
        assertEquals("2026-06-17T21:23:05.123Z", parsed.created)
        assertEquals("Body", parsed.body)
    }
}
