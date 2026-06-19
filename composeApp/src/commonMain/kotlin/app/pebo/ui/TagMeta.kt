package app.pebo.ui

/**
 * Persisted "pop" styling for a single tag — the data behind the *Tags that pop* feature.
 *
 * Everything is null/false by default so an unstyled tag keeps Pebo's normal look. [iconId] is a
 * catalogue id from [TAG_ICONS]; [colorArgb] is a packed `0xAARRGGBB` accent (stored as a [Long] so
 * the model stays free of any Compose dependency and is trivially unit-testable); [pinned] lifts the
 * tag into a dedicated section at the top of the sidebar.
 */
data class TagStyle(
    val iconId: String? = null,
    val colorArgb: Long? = null,
    val pinned: Boolean = false,
) {
    /** True when the tag carries no custom styling (so it can be dropped from storage entirely). */
    val isDefault: Boolean get() = iconId == null && colorArgb == null && !pinned
}

/**
 * The accent palette offered in the tag-style picker (Tailwind-500 hues, packed `0xAARRGGBB`).
 * Curated so every swatch reads clearly on both light and dark surfaces.
 */
val TAG_COLORS: List<Long> = listOf(
    0xFFEF4444L, // red
    0xFFF97316L, // orange
    0xFFF59E0BL, // amber
    0xFFEAB308L, // yellow
    0xFF84CC16L, // lime
    0xFF22C55EL, // green
    0xFF10B981L, // emerald
    0xFF14B8A6L, // teal
    0xFF06B6D4L, // cyan
    0xFF3B82F6L, // blue
    0xFF6366F1L, // indigo
    0xFF8B5CF6L, // violet
    0xFFA855F7L, // purple
    0xFFEC4899L, // pink
    0xFFF43F5EL, // rose
    0xFF64748BL, // slate
)

/**
 * Serialises a [TagStyle] to the compact `iconId;colorHex;pinned01` form stored as one preference
 * value. Tag names only contain `[a-z0-9-_/]`, so neither `;` nor the surrounding key can collide.
 */
fun encodeTagStyle(style: TagStyle): String = buildString {
    append(style.iconId ?: "")
    append(';')
    append(style.colorArgb?.toString(16) ?: "")
    append(';')
    append(if (style.pinned) '1' else '0')
}

/** Parses the [encodeTagStyle] form back into a [TagStyle]; tolerant of empty/partial fields. */
fun decodeTagStyle(raw: String): TagStyle {
    val parts = raw.split(';')
    val iconId = parts.getOrNull(0)?.takeIf { it.isNotEmpty() }
    val colorArgb = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toLongOrNull(16)
    val pinned = parts.getOrNull(2)?.trim() == "1"
    return TagStyle(iconId = iconId, colorArgb = colorArgb, pinned = pinned)
}

/**
 * Pure icon-picker search: keeps every id whose lowercased value prefix- or substring-matches
 * [query], with prefix matches first and the catalogue's original order preserved within each band.
 * A blank query returns the list unchanged. Kept platform-free so it can be unit-tested directly.
 */
fun matchTagIconIds(ids: List<String>, query: String): List<String> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return ids
    val prefix = ArrayList<String>()
    val contains = ArrayList<String>()
    for (id in ids) {
        val low = id.lowercase()
        when {
            low.startsWith(q) -> prefix.add(id)
            low.contains(q) -> contains.add(id)
        }
    }
    return prefix + contains
}
