package app.pebo.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagMetaTest {

    @Test
    fun encodeDecodeRoundTripsAFullStyle() {
        val style = TagStyle(iconId = "rocket", colorArgb = 0xFF3B82F6L, pinned = true)
        assertEquals("rocket;ff3b82f6;1", encodeTagStyle(style))
        assertEquals(style, decodeTagStyle(encodeTagStyle(style)))
    }

    @Test
    fun encodeDecodeRoundTripsTheDefaultStyle() {
        val style = TagStyle()
        assertEquals(";;0", encodeTagStyle(style))
        val back = decodeTagStyle(encodeTagStyle(style))
        assertEquals(style, back)
        assertTrue(back.isDefault)
    }

    @Test
    fun encodeDecodeRoundTripsPartialStyles() {
        val iconOnly = TagStyle(iconId = "star")
        assertEquals(iconOnly, decodeTagStyle(encodeTagStyle(iconOnly)))

        val colorOnly = TagStyle(colorArgb = 0xFFEF4444L)
        assertEquals(colorOnly, decodeTagStyle(encodeTagStyle(colorOnly)))

        val pinnedOnly = TagStyle(pinned = true)
        assertEquals(pinnedOnly, decodeTagStyle(encodeTagStyle(pinnedOnly)))
    }

    @Test
    fun isDefaultDistinguishesStyledFromUnstyled() {
        assertTrue(TagStyle().isDefault)
        assertFalse(TagStyle(iconId = "rocket").isDefault)
        assertFalse(TagStyle(colorArgb = 0xFF22C55EL).isDefault)
        assertFalse(TagStyle(pinned = true).isDefault)
    }

    @Test
    fun decodeToleratesEmptyAndPartialInput() {
        assertEquals(TagStyle(), decodeTagStyle(""))
        assertEquals(TagStyle(iconId = "flag"), decodeTagStyle("flag"))
        assertEquals(TagStyle(iconId = "flag"), decodeTagStyle("flag;"))
        assertEquals(TagStyle(colorArgb = 0xFFEAB308L), decodeTagStyle(";ffeab308"))
        assertNull(decodeTagStyle(";nothex;0").colorArgb)
    }

    @Test
    fun allPaletteColorsSurviveEncoding() {
        for (color in TAG_COLORS) {
            val style = TagStyle(colorArgb = color)
            assertEquals(color, decodeTagStyle(encodeTagStyle(style)).colorArgb)
        }
    }

    @Test
    fun matchRanksPrefixBeforeSubstringAndKeepsOrder() {
        val ids = listOf("calendar-today", "calendar-month", "wine-bar", "bar-chart")
        assertEquals(
            listOf("bar-chart", "wine-bar"),
            matchTagIconIds(ids, "bar"),
        )
        assertEquals(
            listOf("calendar-today", "calendar-month"),
            matchTagIconIds(ids, "calendar"),
        )
    }

    @Test
    fun matchIsCaseInsensitiveAndBlankReturnsAll() {
        val ids = listOf("rocket", "work")
        assertEquals(ids, matchTagIconIds(ids, "   "))
        assertEquals(listOf("rocket"), matchTagIconIds(ids, "ROCK"))
        assertTrue(matchTagIconIds(ids, "zzz").isEmpty())
    }

    @Test
    fun catalogueHasMoreThan250UniqueSearchableIcons() {
        assertTrue(TAG_ICONS.size >= 250, "expected 250+ icons, got ${TAG_ICONS.size}")
        assertEquals(TAG_ICONS.size, TAG_ICON_IDS.toSet().size, "icon ids must be unique")
        // Every id resolves back to its vector.
        for (icon in TAG_ICONS) assertTrue(tagIconById(icon.id) === icon.image)
        assertNull(tagIconById("definitely-not-an-icon"))
        assertNull(tagIconById(null))
    }
}
