package app.pebo.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mermaid is rendered natively (no JS), so the grammar parsers are the correctness boundary. These
 * guard flowchart shape/label/edge parsing, sequence message parsing, layer assignment (including
 * cycle termination), and graceful rejection of unsupported diagram kinds.
 */
class MermaidTest {

    @Test
    fun flowchartParsesShapesLabelsAndEdges() {
        val diagram = parseMermaid(
            """
            graph TD
              A[Start] --> B{Decision}
              B -->|Yes| C(Go)
              B -->|No| D
            """.trimIndent(),
        )
        val fc = diagram as Flowchart
        assertEquals(FlowDir.TD, fc.dir)
        assertEquals(listOf("A", "B", "C", "D"), fc.nodes.map { it.id })

        val byId = fc.nodes.associateBy { it.id }
        assertEquals(FlowNode("A", "Start", NodeShape.RECT), byId.getValue("A"))
        assertEquals(FlowNode("B", "Decision", NodeShape.DIAMOND), byId.getValue("B"))
        assertEquals(FlowNode("C", "Go", NodeShape.ROUND), byId.getValue("C"))
        // A bare reference keeps its id as the label.
        assertEquals(FlowNode("D", "D", NodeShape.RECT), byId.getValue("D"))

        assertEquals(3, fc.edges.size)
        assertEquals(FlowEdge("A", "B", null, EdgeStyle.SOLID, true), fc.edges[0])
        assertEquals(FlowEdge("B", "C", "Yes", EdgeStyle.SOLID, true), fc.edges[1])
        assertEquals(FlowEdge("B", "D", "No", EdgeStyle.SOLID, true), fc.edges[2])
    }

    @Test
    fun flowchartChainAndDirectionAreParsed() {
        val fc = parseMermaid("flowchart LR\nA --> B --> C") as Flowchart
        assertEquals(FlowDir.LR, fc.dir)
        assertEquals(listOf("A" to "B", "B" to "C"), fc.edges.map { it.from to it.to })
    }

    @Test
    fun dashLabelSyntaxIsNormalized() {
        val fc = parseMermaid("graph TD\nA -- pay --> B") as Flowchart
        assertEquals("pay", fc.edges.single().label)
    }

    @Test
    fun dottedAndThickEdgeStylesAreDetected() {
        val fc = parseMermaid("graph TD\nA -.-> B\nB ==> C") as Flowchart
        assertEquals(EdgeStyle.DOTTED, fc.edges[0].style)
        assertEquals(EdgeStyle.THICK, fc.edges[1].style)
    }

    @Test
    fun explicitShapeWinsOverEarlierBareReference() {
        // A is first seen as a bare target, then later defined with a shape: the shape must win.
        val fc = parseMermaid("graph TD\nX --> A\nA[Real Label] --> Y") as Flowchart
        val a = fc.nodes.single { it.id == "A" }
        assertEquals("Real Label", a.label)
        assertEquals(NodeShape.RECT, a.shape)
    }

    @Test
    fun sequenceParsesParticipantsAndMessages() {
        val sq = parseMermaid(
            """
            sequenceDiagram
              participant A as Alice
              A->>B: Hello
              B-->>A: Hi back
            """.trimIndent(),
        ) as SequenceDiagram

        assertEquals(listOf("A", "B"), sq.participants.map { it.id })
        assertEquals("Alice", sq.participants.first { it.id == "A" }.label)
        // B is implicit, so it falls back to its id as the label.
        assertEquals("B", sq.participants.first { it.id == "B" }.label)

        assertEquals(2, sq.messages.size)
        assertEquals(SeqMessage("A", "B", "Hello", SeqLineStyle.SOLID), sq.messages[0])
        assertEquals(SeqMessage("B", "A", "Hi back", SeqLineStyle.DASHED), sq.messages[1])
    }

    @Test
    fun assignLayersUsesLongestPath() {
        val layers = assignLayers(listOf("A", "B", "C"), listOf("A" to "B", "B" to "C", "A" to "C"))
        assertEquals(0, layers["A"])
        assertEquals(1, layers["B"])
        assertEquals(2, layers["C"])
    }

    @Test
    fun assignLayersTerminatesOnCycles() {
        // A two-node cycle must not loop forever; layers stay bounded by the node count.
        val layers = assignLayers(listOf("A", "B"), listOf("A" to "B", "B" to "A"))
        assertTrue(layers.values.all { it <= 2 })
    }

    @Test
    fun unsupportedDiagramKindReturnsNull() {
        assertNull(parseMermaid("pie title Votes\n\"Cats\": 7"))
        assertNull(parseMermaid(""))
    }
}
