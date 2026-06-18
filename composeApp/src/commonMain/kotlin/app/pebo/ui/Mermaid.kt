package app.pebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pebo.ui.theme.LocalMonoFontFamily
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A from-scratch, dependency-free Mermaid renderer. Mermaid is normally a JavaScript library, but
 * Pebo targets native KMP platforms (no webview/JS engine), so this parses the most common diagram
 * grammars (flowcharts + sequence diagrams) into a pure model and draws them with Compose Canvas.
 * Parsing lives in [parseMermaid] / [assignLayers] (unit-tested); anything unsupported falls back to
 * the raw fenced source so nothing is ever lost.
 */

internal sealed interface MermaidDiagram

internal enum class FlowDir { TD, LR }

internal enum class NodeShape { RECT, ROUND, STADIUM, CIRCLE, DIAMOND, HEX }

internal enum class EdgeStyle { SOLID, DOTTED, THICK }

internal data class FlowNode(val id: String, val label: String, val shape: NodeShape)

internal data class FlowEdge(
    val from: String,
    val to: String,
    val label: String?,
    val style: EdgeStyle,
    val arrow: Boolean,
)

internal data class Flowchart(
    val dir: FlowDir,
    val nodes: List<FlowNode>,
    val edges: List<FlowEdge>,
) : MermaidDiagram

internal enum class SeqLineStyle { SOLID, DASHED }

internal data class SeqParticipant(val id: String, val label: String)

internal data class SeqMessage(
    val from: String,
    val to: String,
    val text: String,
    val style: SeqLineStyle,
)

internal data class SequenceDiagram(
    val participants: List<SeqParticipant>,
    val messages: List<SeqMessage>,
) : MermaidDiagram

// ---------------------------------------------------------------------------------------------
// Parsing (pure)
// ---------------------------------------------------------------------------------------------

private val linkRegex = Regex("""(-\.->|-\.-|==>|===|-->|---)(?:\|([^|]*)\|)?""")
private val nodeIdRegex = Regex("""^([A-Za-z0-9_][A-Za-z0-9_.-]*)""")
private val seqMsgRegex =
    Regex("""^([A-Za-z0-9_](?:[\w-]*[A-Za-z0-9_])?)\s*(-->>|->>|--x|-x|-->|->|--\)|-\))\s*([A-Za-z0-9_](?:[\w-]*[A-Za-z0-9_])?)\s*:\s*(.*)$""")

/** Detects the diagram kind from the first non-comment line and dispatches to a grammar parser. */
internal fun parseMermaid(source: String): MermaidDiagram? {
    val lines = source.replace("\r\n", "\n").split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("%%") }
    if (lines.isEmpty()) return null
    val header = lines.first().lowercase()
    return when {
        header.startsWith("sequencediagram") -> parseSequence(lines.drop(1))
        header.startsWith("graph") || header.startsWith("flowchart") ->
            parseFlowchart(parseFlowDir(lines.first()), lines.drop(1))
        else -> null
    }
}

private fun parseFlowDir(header: String): FlowDir {
    val token = header.trim().split(Regex("""\s+""")).getOrNull(1)?.uppercase().orEmpty()
    return if (token.startsWith("LR") || token.startsWith("RL")) FlowDir.LR else FlowDir.TD
}

private fun parseFlowchart(dir: FlowDir, lines: List<String>): MermaidDiagram? {
    val nodes = LinkedHashMap<String, FlowNode>()
    val edges = ArrayList<FlowEdge>()

    fun register(spec: String): String? {
        val parsed = parseNodeSpec(spec) ?: return null
        val existing = nodes[parsed.id]
        // Keep the richest definition: an explicit shape/label wins over a bare id reference.
        if (existing == null || (existing.shape == NodeShape.RECT && existing.label == existing.id &&
                (parsed.shape != NodeShape.RECT || parsed.label != parsed.id))
        ) {
            nodes[parsed.id] = parsed
        }
        return parsed.id
    }

    for (raw in lines) {
        if (raw.startsWith("subgraph") || raw == "end" || raw.startsWith("style") ||
            raw.startsWith("classDef") || raw.startsWith("class ") || raw.startsWith("linkStyle")
        ) continue

        val line = normalizeEdgeLabels(raw)
        val matches = linkRegex.findAll(line).toList()
        if (matches.isEmpty()) {
            register(line)
            continue
        }
        var prev = register(line.substring(0, matches.first().range.first))
        for ((idx, m) in matches.withIndex()) {
            val segStart = m.range.last + 1
            val segEnd = if (idx + 1 < matches.size) matches[idx + 1].range.first else line.length
            val next = register(line.substring(segStart, segEnd))
            val op = m.groupValues[1]
            val label = m.groupValues[2].trim().ifBlank { null }
            val style = when {
                op.startsWith("-.") -> EdgeStyle.DOTTED
                op.startsWith("==") || op == "===" -> EdgeStyle.THICK
                else -> EdgeStyle.SOLID
            }
            val arrow = op.endsWith(">") || op.endsWith("x") || op.endsWith(")")
            if (prev != null && next != null) edges += FlowEdge(prev, next, label, style, arrow)
            prev = next
        }
    }
    if (nodes.isEmpty()) return null
    return Flowchart(dir, nodes.values.toList(), edges)
}

/** Rewrites `A -- text --> B` dash-label syntax into the canonical `A -->|text| B` pipe form. */
private fun normalizeEdgeLabels(line: String): String {
    var s = line
    s = Regex("""--\s*([^->|]+?)\s*-->""").replace(s) { "-->|${it.groupValues[1].trim()}|" }
    s = Regex("""==\s*([^=>|]+?)\s*==>""").replace(s) { "==>|${it.groupValues[1].trim()}|" }
    s = Regex("""-\.\s*([^.>|]+?)\s*\.->""").replace(s) { "-.->|${it.groupValues[1].trim()}|" }
    return s
}

private fun parseNodeSpec(spec: String): FlowNode? {
    val t = spec.trim()
    if (t.isEmpty()) return null
    val id = nodeIdRegex.find(t)?.value ?: return null
    val rest = t.substring(id.length).trim()

    fun clean(label: String) = label.trim().trim('"').trim().ifBlank { id }

    return when {
        rest.startsWith("((") && rest.endsWith("))") ->
            FlowNode(id, clean(rest.substring(2, rest.length - 2)), NodeShape.CIRCLE)
        rest.startsWith("([") && rest.endsWith("])") ->
            FlowNode(id, clean(rest.substring(2, rest.length - 2)), NodeShape.STADIUM)
        rest.startsWith("{{") && rest.endsWith("}}") ->
            FlowNode(id, clean(rest.substring(2, rest.length - 2)), NodeShape.HEX)
        rest.startsWith("[[") && rest.endsWith("]]") ->
            FlowNode(id, clean(rest.substring(2, rest.length - 2)), NodeShape.RECT)
        rest.startsWith("[") && rest.endsWith("]") ->
            FlowNode(id, clean(rest.substring(1, rest.length - 1)), NodeShape.RECT)
        rest.startsWith("(") && rest.endsWith(")") ->
            FlowNode(id, clean(rest.substring(1, rest.length - 1)), NodeShape.ROUND)
        rest.startsWith("{") && rest.endsWith("}") ->
            FlowNode(id, clean(rest.substring(1, rest.length - 1)), NodeShape.DIAMOND)
        else -> FlowNode(id, id, NodeShape.RECT)
    }
}

private fun parseSequence(lines: List<String>): MermaidDiagram? {
    val participants = LinkedHashMap<String, String>()
    val messages = ArrayList<SeqMessage>()

    fun ensure(id: String): String {
        participants.getOrPut(id) { id }
        return id
    }

    for (line in lines) {
        val lower = line.lowercase()
        when {
            lower.startsWith("participant ") || lower.startsWith("actor ") -> {
                val decl = line.substringAfter(' ').trim()
                if (decl.contains(" as ")) {
                    val id = decl.substringBefore(" as ").trim()
                    val label = decl.substringAfter(" as ").trim()
                    participants[id] = label
                } else {
                    participants[decl] = decl
                }
            }
            lower.startsWith("note ") || lower.startsWith("loop") || lower.startsWith("alt") ||
                lower.startsWith("opt") || lower.startsWith("else") || lower == "end" ||
                lower.startsWith("par") || lower.startsWith("activate") ||
                lower.startsWith("deactivate") || lower.startsWith("autonumber") ||
                lower.startsWith("rect") || lower.startsWith("critical") || lower.startsWith("break") ->
                Unit // structural keywords are skipped (still renders the messages around them)
            else -> {
                val m = seqMsgRegex.matchEntire(line) ?: continue
                val from = ensure(m.groupValues[1].trim())
                val to = ensure(m.groupValues[3].trim())
                val op = m.groupValues[2]
                val style = if (op.startsWith("--")) SeqLineStyle.DASHED else SeqLineStyle.SOLID
                messages += SeqMessage(from, to, m.groupValues[4].trim(), style)
            }
        }
    }
    if (participants.isEmpty() && messages.isEmpty()) return null
    return SequenceDiagram(participants.map { SeqParticipant(it.key, it.value) }, messages)
}

/**
 * Longest-path layer assignment for a directed graph. Roots sit at layer 0; every edge pushes its
 * target at least one layer deeper. Growth is capped at the node count so cycles terminate instead
 * of looping forever.
 */
internal fun assignLayers(nodeIds: List<String>, edges: List<Pair<String, String>>): Map<String, Int> {
    val layer = LinkedHashMap<String, Int>()
    nodeIds.forEach { layer[it] = 0 }
    val cap = nodeIds.size
    repeat(cap.coerceAtLeast(1)) {
        var changed = false
        for ((from, to) in edges) {
            val candidate = (layer[from] ?: 0) + 1
            if (candidate <= cap && (layer[to] ?: 0) < candidate) {
                layer[to] = candidate
                changed = true
            }
        }
        if (!changed) return layer
    }
    return layer
}

// ---------------------------------------------------------------------------------------------
// Rendering (Compose Canvas)
// ---------------------------------------------------------------------------------------------

@Composable
fun MermaidView(code: String, modifier: Modifier = Modifier) {
    val diagram = remember(code) { parseMermaid(code) }
    when (diagram) {
        is Flowchart -> DiagramFrame("flowchart", modifier) { FlowchartCanvas(diagram) }
        is SequenceDiagram -> DiagramFrame("sequence", modifier) { SequenceCanvas(diagram) }
        else -> MermaidFallback(code, modifier)
    }
}

@Composable
private fun DiagramFrame(kind: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            )
            .padding(12.dp),
    ) {
        Text(
            kind,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { content() }
    }
}

@Composable
private fun MermaidFallback(code: String, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            )
            .padding(14.dp),
    ) {
        Text(
            "mermaid",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            code,
            fontFamily = LocalMonoFontFamily.current,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private class NodeBox(val w: Float, val h: Float)

@Composable
private fun FlowchartCanvas(fc: Flowchart) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val nodeFill = MaterialTheme.colorScheme.primaryContainer
    val nodeBorder = MaterialTheme.colorScheme.primary
    val nodeText = MaterialTheme.colorScheme.onPrimaryContainer
    val edgeColor = MaterialTheme.colorScheme.outline
    val edgeTextColor = MaterialTheme.colorScheme.onSurface
    val labelBg = MaterialTheme.colorScheme.surface

    val labelStyle = TextStyle(fontSize = 13.sp, color = nodeText)
    val edgeStyle = TextStyle(fontSize = 11.sp, color = edgeTextColor)

    val labelLayouts = remember(fc, labelStyle) {
        fc.nodes.associate { it.id to measurer.measure(AnnotatedString(it.label), labelStyle) }
    }
    val edgeLayouts = remember(fc, edgeStyle) {
        fc.edges.mapNotNull { it.label }.distinct()
            .associateWith { measurer.measure(AnnotatedString(it), edgeStyle) }
    }

    val padX = with(density) { 16.dp.toPx() }
    val baseH = with(density) { 46.dp.toPx() }
    val minW = with(density) { 58.dp.toPx() }
    val hGap = with(density) { 46.dp.toPx() }
    val vGap = with(density) { 48.dp.toPx() }
    val margin = with(density) { 18.dp.toPx() }

    val sizes = fc.nodes.associate { n ->
        val tl = labelLayouts.getValue(n.id)
        var w = maxOf(minW, tl.size.width + padX * 2)
        var h = baseH
        when (n.shape) {
            NodeShape.CIRCLE -> {
                val d = maxOf(w, tl.size.height + padX * 2)
                w = d; h = d
            }
            NodeShape.DIAMOND, NodeShape.HEX -> {
                w += padX * 2; h = baseH + with(density) { 10.dp.toPx() }
            }
            else -> {}
        }
        n.id to NodeBox(w, h)
    }

    val layerMap = assignLayers(fc.nodes.map { it.id }, fc.edges.map { it.from to it.to })
    val maxLayer = layerMap.values.maxOrNull() ?: 0
    val byLayer = (0..maxLayer).map { L -> fc.nodes.filter { (layerMap[it.id] ?: 0) == L } }

    val centers = HashMap<String, Offset>()
    val totalW: Float
    val totalH: Float

    if (fc.dir == FlowDir.TD) {
        val layerWidths = byLayer.map { row ->
            row.sumOf { sizes.getValue(it.id).w.toDouble() }.toFloat() +
                hGap * (row.size - 1).coerceAtLeast(0)
        }
        totalW = (layerWidths.maxOrNull() ?: minW) + margin * 2
        var y = margin
        for ((idx, row) in byLayer.withIndex()) {
            val rowH = row.maxOfOrNull { sizes.getValue(it.id).h } ?: baseH
            var x = (totalW - layerWidths[idx]) / 2f
            for (n in row) {
                val s = sizes.getValue(n.id)
                centers[n.id] = Offset(x + s.w / 2f, y + rowH / 2f)
                x += s.w + hGap
            }
            y += rowH + vGap
        }
        totalH = y - vGap + margin
    } else {
        val layerHeights = byLayer.map { col ->
            col.sumOf { sizes.getValue(it.id).h.toDouble() }.toFloat() +
                vGap * (col.size - 1).coerceAtLeast(0)
        }
        totalH = (layerHeights.maxOrNull() ?: baseH) + margin * 2
        var x = margin
        for ((idx, col) in byLayer.withIndex()) {
            val colW = col.maxOfOrNull { sizes.getValue(it.id).w } ?: minW
            var y = (totalH - layerHeights[idx]) / 2f
            for (n in col) {
                val s = sizes.getValue(n.id)
                centers[n.id] = Offset(x + colW / 2f, y + s.h / 2f)
                y += s.h + vGap
            }
            x += colW + hGap
        }
        totalW = x - hGap + margin
    }

    Canvas(
        Modifier
            .padding(top = 2.dp, bottom = 2.dp)
            .size(with(density) { totalW.toDp() }, with(density) { totalH.toDp() }),
    ) {
        for (e in fc.edges) {
            val a = centers[e.from] ?: continue
            val b = centers[e.to] ?: continue
            val sa = sizes.getValue(e.from)
            val sb = sizes.getValue(e.to)
            val start = clipToBox(a, sa.w, sa.h, b)
            val end = clipToBox(b, sb.w, sb.h, a)
            drawEdge(start, end, e.style, e.arrow, edgeColor)
            val tl = e.label?.let { edgeLayouts[it] }
            if (tl != null) {
                val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                val tlX = mid.x - tl.size.width / 2f
                val tlY = mid.y - tl.size.height / 2f
                drawRect(
                    labelBg,
                    topLeft = Offset(tlX - 4f, tlY - 1f),
                    size = Size(tl.size.width + 8f, tl.size.height + 2f),
                )
                drawText(tl, color = edgeTextColor, topLeft = Offset(tlX, tlY))
            }
        }
        for (n in fc.nodes) {
            val c = centers[n.id] ?: continue
            val s = sizes.getValue(n.id)
            drawNode(n.shape, c, s.w, s.h, nodeFill, nodeBorder)
            val tl = labelLayouts.getValue(n.id)
            drawText(
                tl,
                color = nodeText,
                topLeft = Offset(c.x - tl.size.width / 2f, c.y - tl.size.height / 2f),
            )
        }
    }
}

@Composable
private fun SequenceCanvas(sq: SequenceDiagram) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val boxFill = MaterialTheme.colorScheme.primaryContainer
    val boxBorder = MaterialTheme.colorScheme.primary
    val boxText = MaterialTheme.colorScheme.onPrimaryContainer
    val lineColor = MaterialTheme.colorScheme.outline
    val lifelineColor = MaterialTheme.colorScheme.outlineVariant
    val msgTextColor = MaterialTheme.colorScheme.onSurface

    val labelStyle = TextStyle(fontSize = 13.sp, color = boxText, fontWeight = FontWeight.Medium)
    val msgStyle = TextStyle(fontSize = 12.sp, color = msgTextColor)

    val pLayouts = remember(sq, labelStyle) {
        sq.participants.associate { it.id to measurer.measure(AnnotatedString(it.label), labelStyle) }
    }
    val mLayouts = remember(sq, msgStyle) {
        sq.messages.map { measurer.measure(AnnotatedString(it.text), msgStyle) }
    }

    val padX = with(density) { 16.dp.toPx() }
    val boxH = with(density) { 40.dp.toPx() }
    val minBoxW = with(density) { 76.dp.toPx() }
    val colGap = with(density) { 44.dp.toPx() }
    val margin = with(density) { 18.dp.toPx() }
    val headGap = with(density) { 34.dp.toPx() }
    val rowH = with(density) { 40.dp.toPx() }

    val boxW = sq.participants.associate { p ->
        p.id to maxOf(minBoxW, pLayouts.getValue(p.id).size.width + padX * 2)
    }
    val centerX = LinkedHashMap<String, Float>()
    var x = margin
    for (p in sq.participants) {
        val w = boxW.getValue(p.id)
        centerX[p.id] = x + w / 2f
        x += w + colGap
    }
    val totalW = (x - colGap + margin).coerceAtLeast(minBoxW + margin * 2)
    val topY = margin
    val firstMsgY = topY + boxH + headGap
    val totalH = firstMsgY + sq.messages.size * rowH + margin

    Canvas(
        Modifier.size(with(density) { totalW.toDp() }, with(density) { totalH.toDp() }),
    ) {
        val lifelineEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        for (p in sq.participants) {
            val cx = centerX.getValue(p.id)
            drawLine(
                lifelineColor,
                Offset(cx, topY + boxH),
                Offset(cx, totalH - margin),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = lifelineEffect,
            )
        }
        for (p in sq.participants) {
            val cx = centerX.getValue(p.id)
            val w = boxW.getValue(p.id)
            val left = cx - w / 2f
            drawRoundRect(
                boxFill,
                topLeft = Offset(left, topY),
                size = Size(w, boxH),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
            drawRoundRect(
                boxBorder,
                topLeft = Offset(left, topY),
                size = Size(w, boxH),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(1.5.dp.toPx()),
            )
            val tl = pLayouts.getValue(p.id)
            drawText(
                tl,
                color = boxText,
                topLeft = Offset(cx - tl.size.width / 2f, topY + boxH / 2f - tl.size.height / 2f),
            )
        }
        for ((k, m) in sq.messages.withIndex()) {
            val y = firstMsgY + k * rowH
            val x1 = centerX[m.from] ?: continue
            val x2 = centerX[m.to] ?: continue
            val tl = mLayouts[k]
            val effect = if (m.style == SeqLineStyle.DASHED) {
                PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
            } else {
                null
            }
            if (m.from == m.to) {
                val loopW = 34.dp.toPx()
                val topLineY = y - 8.dp.toPx()
                drawLine(lineColor, Offset(x1, topLineY), Offset(x1 + loopW, topLineY), strokeWidth = 1.5.dp.toPx(), pathEffect = effect)
                drawLine(lineColor, Offset(x1 + loopW, topLineY), Offset(x1 + loopW, y), strokeWidth = 1.5.dp.toPx(), pathEffect = effect)
                drawLine(lineColor, Offset(x1 + loopW, y), Offset(x1, y), strokeWidth = 1.5.dp.toPx(), pathEffect = effect)
                drawArrowHead(Offset(x1, y), Offset(-1f, 0f), lineColor)
                drawText(tl, color = msgTextColor, topLeft = Offset(x1 + loopW + 6.dp.toPx(), y - tl.size.height / 2f - 4.dp.toPx()))
            } else {
                drawText(
                    tl,
                    color = msgTextColor,
                    topLeft = Offset((x1 + x2) / 2f - tl.size.width / 2f, y - tl.size.height - 3.dp.toPx()),
                )
                drawLine(lineColor, Offset(x1, y), Offset(x2, y), strokeWidth = 1.5.dp.toPx(), pathEffect = effect)
                drawArrowHead(Offset(x2, y), Offset(if (x2 >= x1) 1f else -1f, 0f), lineColor)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// DrawScope primitives
// ---------------------------------------------------------------------------------------------

/** Clips a center→target ray to [center]'s bounding box, giving the point where an edge touches it. */
private fun clipToBox(center: Offset, w: Float, h: Float, toward: Offset): Offset {
    val dx = toward.x - center.x
    val dy = toward.y - center.y
    if (dx == 0f && dy == 0f) return center
    val hw = w / 2f
    val hh = h / 2f
    val tx = if (dx != 0f) hw / abs(dx) else Float.MAX_VALUE
    val ty = if (dy != 0f) hh / abs(dy) else Float.MAX_VALUE
    val t = min(tx, ty)
    return Offset(center.x + dx * t, center.y + dy * t)
}

private fun DrawScope.drawEdge(start: Offset, end: Offset, style: EdgeStyle, arrow: Boolean, color: Color) {
    val strokeW = when (style) {
        EdgeStyle.THICK -> 2.5.dp.toPx()
        else -> 1.5.dp.toPx()
    }
    val effect = if (style == EdgeStyle.DOTTED) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null
    drawLine(color, start, end, strokeWidth = strokeW, cap = StrokeCap.Round, pathEffect = effect)
    if (arrow) {
        val dir = Offset(end.x - start.x, end.y - start.y)
        drawArrowHead(end, dir, color)
    }
}

private fun DrawScope.drawArrowHead(tip: Offset, dir: Offset, color: Color) {
    val len = 9.dp.toPx()
    val spread = 0.42f
    val ang = atan2(dir.y, dir.x)
    val a1 = ang + 3.1415927f - spread
    val a2 = ang + 3.1415927f + spread
    val p1 = Offset(tip.x + len * cos(a1), tip.y + len * sin(a1))
    val p2 = Offset(tip.x + len * cos(a2), tip.y + len * sin(a2))
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawNode(shape: NodeShape, c: Offset, w: Float, h: Float, fill: Color, border: Color) {
    val left = c.x - w / 2f
    val top = c.y - h / 2f
    val bw = 1.5.dp.toPx()
    when (shape) {
        NodeShape.RECT -> roundRectNode(left, top, w, h, 6.dp.toPx(), fill, border, bw)
        NodeShape.ROUND -> roundRectNode(left, top, w, h, 14.dp.toPx(), fill, border, bw)
        NodeShape.STADIUM -> roundRectNode(left, top, w, h, h / 2f, fill, border, bw)
        NodeShape.CIRCLE -> {
            drawCircle(fill, radius = w / 2f, center = c)
            drawCircle(border, radius = w / 2f, center = c, style = Stroke(bw))
        }
        NodeShape.DIAMOND -> {
            val path = Path().apply {
                moveTo(c.x, top)
                lineTo(left + w, c.y)
                lineTo(c.x, top + h)
                lineTo(left, c.y)
                close()
            }
            drawPath(path, fill)
            drawPath(path, border, style = Stroke(bw))
        }
        NodeShape.HEX -> {
            val inset = w * 0.22f
            val path = Path().apply {
                moveTo(left + inset, top)
                lineTo(left + w - inset, top)
                lineTo(left + w, c.y)
                lineTo(left + w - inset, top + h)
                lineTo(left + inset, top + h)
                lineTo(left, c.y)
                close()
            }
            drawPath(path, fill)
            drawPath(path, border, style = Stroke(bw))
        }
    }
}

private fun DrawScope.roundRectNode(
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    radius: Float,
    fill: Color,
    border: Color,
    bw: Float,
) {
    val cr = CornerRadius(radius)
    drawRoundRect(fill, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = cr)
    drawRoundRect(border, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = cr, style = Stroke(bw))
}
