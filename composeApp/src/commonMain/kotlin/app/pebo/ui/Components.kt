package app.pebo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VPaneDivider() {
    Box(
        Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    )
}

@Composable
fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
    indentLevel: Int = 0,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val target = when {
        selected -> scheme.primary.copy(alpha = 0.14f)
        hovered -> scheme.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(target, label = "sidebarBg")
    val tint = if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.82f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = (10 + indentLevel * 14).dp, end = 10.dp)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null && count > 0) {
            CountPill(count)
        }
    }
}

@Composable
fun TagChip(tag: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            "#$tag",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.0.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.64f),
        modifier = modifier,
    )
}

@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
            modifier = Modifier
                .fillMaxWidth(0.78f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AccentBar(width = 42.dp)
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (actionText != null && onAction != null) {
                    Spacer(Modifier.height(2.dp))
                    PrimaryAction(label = actionText, onClick = onAction)
                }
            }
        }
    }
}

val chipSpacing = Arrangement.spacedBy(4.dp)

@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)),
        modifier = modifier.height(34.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = if (icon == null) 18.dp else 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
    }
}

@Composable
fun PillAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (icon == null) 12.dp else 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun AccentBar(width: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(width)
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
fun CountPill(count: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/**
 * Shared file-tree connector rails used by both the note list and the tag tree. One 22dp column per
 * ancestor; `guides[i] == true` draws a continuing `│` rail at that column. The final column is this
 * row's own connector: `true` → a `├` tee (more siblings follow), `false` → a `└` elbow (last child).
 * [connectorY] is where the horizontal arm meets the label — near the top for tall note rows, at the
 * vertical centre for fixed-height sidebar rows.
 */
@Composable
fun TreeRail(
    guides: List<Boolean>,
    color: Color,
    modifier: Modifier = Modifier,
    connectorY: Dp = 11.dp,
) {
    Canvas(
        modifier = modifier
            .width((22 * guides.size).dp)
            .fillMaxHeight(),
    ) {
        if (guides.isEmpty()) return@Canvas
        val colPx = 22.dp.toPx()
        val stroke = 1.8.dp.toPx()
        val armY = connectorY.toPx().coerceAtMost(size.height)
        val last = guides.lastIndex
        guides.forEachIndexed { i, continues ->
            val centerX = i * colPx + colPx / 2f
            if (i < last) {
                if (continues) {
                    drawLine(color, Offset(centerX, 0f), Offset(centerX, size.height), stroke, StrokeCap.Round)
                }
            } else {
                drawLine(color, Offset(centerX, 0f), Offset(centerX, armY), stroke, StrokeCap.Round)
                if (continues) {
                    drawLine(color, Offset(centerX, armY), Offset(centerX, size.height), stroke, StrokeCap.Round)
                }
                drawLine(color, Offset(centerX, armY), Offset(i * colPx + colPx, armY), stroke, StrokeCap.Round)
            }
        }
    }
}
