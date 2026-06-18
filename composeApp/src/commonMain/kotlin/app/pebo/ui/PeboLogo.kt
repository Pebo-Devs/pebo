package app.pebo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Pebo's brand identity, expressed once as fixed colours so the mark stays consistent across every
 * theme, platform and surface (a logo should not recolour with the UI palette). The gradient is a
 * modern indigo→violet sweep; the monogram sits in pure white for maximum contrast.
 */
object PeboBrand {
    val GradientTop = Color(0xFF5B8CFF)   // azure indigo (top-left of the tile)
    val GradientBottom = Color(0xFF7C5CFF) // violet (bottom-right of the tile)
    val Mark = Color(0xFFFFFFFF)           // the white "P"
    val FoldShade = Color(0x33000000)      // subtle dog-ear shadow on the page fold
}

/**
 * The Pebo logo: a rounded-square "page" tile carrying the brand gradient, a folded top-right corner
 * (a notes/document cue), and a clean white **P** monogram whose bowl is a full circle — a friendly,
 * pebble-like head that makes the letter ownable rather than a default typeface "P".
 *
 * Built as an [ImageVector] so it is razor-sharp at every size: a 16px window icon or a 512px export
 * render from the exact same geometry. Used in the sidebar header, the Settings → About panel and as
 * the desktop window / taskbar icon.
 *
 * Viewport is 48×48. The mark geometry is intentionally identical to `branding/pebo-logo.svg`.
 */
fun peboLogo(): ImageVector = ImageVector.Builder(
    name = "PeboLogo",
    defaultWidth = 48.dp,
    defaultHeight = 48.dp,
    viewportWidth = 48f,
    viewportHeight = 48f,
).apply {
    val fold = 13f          // size of the dog-ear fold
    val r = 11f             // tile corner radius
    val tileBrush = Brush.linearGradient(
        colors = listOf(PeboBrand.GradientTop, PeboBrand.GradientBottom),
        start = Offset(2f, 2f),
        end = Offset(46f, 46f),
    )

    // Tile: rounded square whose top-right corner is sliced off into a page fold.
    path(fill = tileBrush) {
        moveTo(r, 0f)
        lineTo(48f - fold, 0f)        // top edge, stopping before the fold
        lineTo(48f, fold)             // diagonal cut of the dog-ear
        lineTo(48f, 48f - r)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, 48f - r, 48f) // bottom-right
        lineTo(r, 48f)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 48f - r)  // bottom-left
        lineTo(0f, r)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, r, 0f)         // top-left
        close()
    }

    // The folded-over flap: a small triangle giving the dog-ear depth.
    path(fill = SolidColor(PeboBrand.FoldShade)) {
        moveTo(48f - fold, 0f)
        lineTo(48f, fold)
        lineTo(48f - fold, fold)
        close()
    }

    // The "P": stem rising from the baseline, then a full-circle bowl at the top. One continuous
    // stroked subpath so the round caps/joins fuse the stem and bowl into a single solid glyph.
    path(
        stroke = SolidColor(PeboBrand.Mark),
        strokeLineWidth = 5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(18f, 37f)
        lineTo(18f, 11f)
        arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 18f, 25f)
    }
}.build()
