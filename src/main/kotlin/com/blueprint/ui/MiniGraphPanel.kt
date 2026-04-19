package com.blueprint.ui

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Read-only mini graph for demo clarity. It lays nodes out by dependency wave
 * and draws simple dependency lines. No editing, dragging, or scheduling lives
 * here.
 */
class MiniGraphPanel : JPanel() {
    private object Theme {
        val Background = Color(0x1E1E1E)
        val Dot = Color(0x2B2B2B)
        val Surface = Color(0x252526)
        val SurfaceSoft = Color(0x2A2A2A)
        val Border = Color(0x3C3C3C)
        val TextStrong = Color(0xFFFFFF)
        val Muted = Color(0x9DA3AF)
        val Accent = Color(0x4FC1FF)
        val AccentSurface = Color(0x102F42)
        val Success = Color(0x6A9955)
        val SuccessSurface = Color(0x1F3826)
        val Warning = Color(0xDCDCAA)
        val WarningSurface = Color(0x3A331E)
        val Danger = Color(0xF48771)
        val DangerSurface = Color(0x3D2420)
        val PurpleSurface = Color(0x2B2842)
    }

    enum class NodeOrigin {
        CODE,
        PROPOSED_UML,
        WORKFLOW,
    }

    data class NodeView(
        val id: String,
        val title: String,
        val status: String,
        val wave: Int,
        val dependencies: List<String>,
        val selected: Boolean,
        val ready: Boolean,
        val blocked: Boolean,
        val detail: String,
        val origin: NodeOrigin = NodeOrigin.WORKFLOW,
        val kind: String = "",
        val source: String = "",
        val sourcePath: String = "",
        val sourceLine: Int? = null,
        val preview: String = "",
        val fields: List<String> = emptyList(),
        val fieldOverflowCount: Int = 0,
        val methods: List<String> = emptyList(),
        val methodOverflowCount: Int = 0,
        val relationshipHint: String = "",
        val groupTitle: String = "",
        val groupOrder: Int = 0,
    )

    private var nodes: List<NodeView> = emptyList()
    private var cards: Map<String, RoundRectangle2D.Float> = emptyMap()
    private var sourceBadges: Map<String, RoundRectangle2D.Float> = emptyMap()
    var onNodeSelected: ((String) -> Unit)? = null
    var onNodeOpenSource: ((NodeView) -> Unit)? = null

    // Drag-to-pan state
    private var dragOrigin: Point? = null

    var zoom: Double = 1.0
        private set
    private val zoomMin = 0.25
    private val zoomMax = 3.0
    private val zoomStep = 0.15

    fun zoomIn() { applyZoom(zoom + zoomStep) }
    fun zoomOut() { applyZoom(zoom - zoomStep) }
    fun zoomReset() { applyZoom(1.0) }

    private fun applyZoom(target: Double, pivotX: Double = width / 2.0, pivotY: Double = height / 2.0) {
        val oldZoom = zoom
        zoom = target.coerceIn(zoomMin, zoomMax)
        if (zoom == oldZoom) return
        updateCanvasSize()
        revalidate()
        val viewport = SwingUtilities.getAncestorOfClass(javax.swing.JViewport::class.java, this)
            as? javax.swing.JViewport ?: run { repaint(); return }
        val vp = viewport.viewPosition
        val ratio = zoom / oldZoom
        val newX = (pivotX * (ratio - 1) + vp.x).toInt().coerceAtLeast(0)
        val newY = (pivotY * (ratio - 1) + vp.y).toInt().coerceAtLeast(0)
        viewport.viewPosition = Point(newX, newY)
        repaint()
    }

    init {
        preferredSize = Dimension(900, 420)
        minimumSize = Dimension(520, 300)
        background = Theme.Background
        toolTipText = ""
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dragOrigin = e.point
                cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
            }

            override fun mouseReleased(e: MouseEvent) {
                dragOrigin = null
                updateCursor(e.point)
            }

            override fun mouseClicked(e: MouseEvent) {
                nodeAt(e.point)?.let { node ->
                    if (e.clickCount >= 2 || sourceBadgeAt(e.point)?.id == node.id) {
                        if (node.hasSourceTarget()) {
                            onNodeOpenSource?.invoke(node)
                        } else {
                            onNodeSelected?.invoke(node.id)
                        }
                        repaint()
                        return
                    }
                    onNodeSelected?.invoke(node.id)
                    repaint()
                }
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                updateCursor(e.point)
            }

            override fun mouseDragged(e: MouseEvent) {
                val origin = dragOrigin ?: return
                val viewport = SwingUtilities.getAncestorOfClass(javax.swing.JViewport::class.java, this@MiniGraphPanel)
                    as? javax.swing.JViewport ?: return
                val vp = viewport.viewPosition
                val dx = origin.x - e.x
                val dy = origin.y - e.y
                val maxX = (preferredSize.width - viewport.width).coerceAtLeast(0)
                val maxY = (preferredSize.height - viewport.height).coerceAtLeast(0)
                viewport.viewPosition = Point(
                    (vp.x + dx).coerceIn(0, maxX),
                    (vp.y + dy).coerceIn(0, maxY),
                )
                // dragOrigin stays fixed — delta is relative to the press point,
                // not accumulated across drag events, so no drift.
                dragOrigin = e.point
            }
        })
        addMouseWheelListener { e ->
            // All vertical scroll events zoom (matches IntelliJ diagram viewer convention).
            // On macOS, two-finger scroll and pinch both arrive as plain MouseWheelEvent
            // without Ctrl unless the user has enabled Accessibility → Zoom scroll gesture.
            // Panning is handled by click-and-drag instead.
            val delta = -e.preciseWheelRotation * zoomStep
            applyZoom(zoom + delta, pivotX = e.x.toDouble(), pivotY = e.y.toDouble())
            e.consume()
        }
    }

    fun setGraph(newNodes: List<NodeView>) {
        nodes = newNodes
        updateCanvasSize()
        revalidate()
        repaint()
    }

    private fun updateCursor(point: Point) {
        cursor = when {
            sourceBadgeAt(point) != null -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            nodeAt(point) != null -> Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
            else -> Cursor.getDefaultCursor()
        }
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val sourceNode = sourceBadgeAt(event.point)
        if (sourceNode != null) {
            return "<html><b>${escape(sourceNode.title)}</b><br/>Open the source file for this code-backed card.</html>"
        }
        return nodeAt(event.point)?.let { node ->
            "<html><b>${escape(node.title)}</b><br/>" +
                "ID: ${node.id.take(8)}<br/>" +
                "Origin: ${node.origin.label()}<br/>" +
                "Status: ${node.status}<br/>" +
                node.kind.takeIf { it.isNotBlank() }?.let { "Kind: ${escape(it)}<br/>" }.orEmpty() +
                "Source: ${escape(node.sourceDescription())}<br/>" +
                node.hasSourceTarget().takeIf { it }?.let { "Use the open file badge or double-click to open the source file.<br/>" }.orEmpty() +
                "Wave: ${node.wave}<br/>" +
                node.summaryLine("Fields", node.fields, node.fieldOverflowCount).takeIf { it.isNotBlank() }
                    ?.let { "${escape(it)}<br/>" }.orEmpty() +
                node.summaryLine("Methods", node.methods, node.methodOverflowCount).takeIf { it.isNotBlank() }
                    ?.let { "${escape(it)}<br/>" }.orEmpty() +
                node.relationshipHint.takeIf { it.isNotBlank() }?.let { "${escape(it)}<br/>" }.orEmpty() +
                escape(node.detail).replace("\n", "<br/>") +
                "</html>"
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paintGraph(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun paintGraph(g: Graphics2D) {
        paintDotGrid(g)
        g.scale(zoom, zoom)
        if (nodes.isEmpty()) {
            g.color = Theme.Muted
            g.font = font.deriveFont(Font.PLAIN, 13f)
            g.drawString("Click Abstract Code to UML to draw this project as architecture.", 24, 38)
            cards = emptyMap()
            sourceBadges = emptyMap()
            return
        }

        val layoutGroups = layoutGroups()
        val richCards = nodes.any { it.hasRichFacts() }
        val cardW = if (richCards) 292f else 230f
        val cardH = if (richCards) 132f else 76f
        val gapX = 70f
        val gapY = 24f
        val startX = 24f
        val startY = 46f
        val localCards = mutableMapOf<String, RoundRectangle2D.Float>()
        val localSourceBadges = mutableMapOf<String, RoundRectangle2D.Float>()

        layoutGroups.forEachIndexed { groupIndex, group ->
            val x = startX + groupIndex * (cardW + gapX)
            g.color = Theme.TextStrong
            g.font = font.deriveFont(Font.BOLD, 12f)
            g.drawString(group.title.ellipsizeToWidth(g, cardW.toInt()), x.toInt(), 28)
            group.nodes.forEachIndexed { row, node ->
                val y = startY + row * (cardH + gapY)
                localCards[node.id] = RoundRectangle2D.Float(x, y, cardW, cardH, 10f, 10f)
            }
        }

        g.stroke = BasicStroke(1.6f)
        nodes.forEach { node ->
            val to = localCards[node.id] ?: return@forEach
            for (depId in node.dependencies) {
                val from = localCards[depId] ?: continue
                g.color = Theme.Border
                val x1 = (from.x + from.width).toInt()
                val y1 = (from.y + from.height / 2).toInt()
                val x2 = to.x.toInt()
                val y2 = (to.y + to.height / 2).toInt()
                g.drawLine(x1, y1, x2, y2)
                g.drawLine(x2, y2, x2 - 6, y2 - 4)
                g.drawLine(x2, y2, x2 - 6, y2 + 4)
            }
        }

        nodes.forEach { node ->
            val card = localCards[node.id] ?: return@forEach
            val fill = colorFor(node)
            g.color = fill
            g.fill(card)
            g.color = if (node.selected) Theme.Accent else borderFor(node)
            g.stroke = BasicStroke(if (node.selected) 3.0f else 1.3f)
            g.draw(card)

            g.color = Theme.TextStrong
            g.font = font.deriveFont(Font.BOLD, 12f)
            val textLeft = (card.x + 12).toInt()
            val titleRightPadding = if (node.kind.isNotBlank()) 96 else 34
            val textWidth = (card.width - titleRightPadding - 12).toInt().coerceAtLeast(120)
            g.drawString(node.title.ellipsizeToWidth(g, textWidth), textLeft, (card.y + 22).toInt())

            if (node.kind.isNotBlank()) {
                paintPill(
                    g = g,
                    text = node.kind,
                    x = (card.x + card.width - 86).toInt(),
                    y = (card.y + 10).toInt(),
                    fill = kindBadgeFill(node),
                    textColor = kindBadgeText(node),
                )
            }

            g.font = font.deriveFont(Font.PLAIN, 11f)
            g.color = Theme.Muted
            val metadata = node.metadataLine()
            g.drawString(metadata.ellipsizeToWidth(g, (card.width - 24).toInt()), textLeft, (card.y + 40).toInt())

            if (richCards) {
                val fieldLine = node.summaryLine("fields", node.fields, node.fieldOverflowCount)
                val methodLine = node.summaryLine("methods", node.methods, node.methodOverflowCount)
                val relationshipLine = node.relationshipHint.ifBlank {
                    node.preview.ifBlank { node.detail.lineSequence().drop(1).firstOrNull().orEmpty() }
                }
                g.drawString(fieldLine.ellipsizeToWidth(g, (card.width - 24).toInt()), textLeft, (card.y + 60).toInt())
                g.drawString(methodLine.ellipsizeToWidth(g, (card.width - 24).toInt()), textLeft, (card.y + 78).toInt())
                if (relationshipLine.isNotBlank()) {
                    g.drawString(relationshipLine.ellipsizeToWidth(g, (card.width - 24).toInt()), textLeft, (card.y + 96).toInt())
                }
            } else {
                val preview = node.preview.ifBlank { node.detail.lineSequence().drop(1).firstOrNull().orEmpty() }
                if (preview.isNotBlank()) {
                    g.color = Theme.Muted
                    g.drawString(preview.ellipsize(34), textLeft, (card.y + 60).toInt())
                }
            }

            if (node.hasSourceTarget()) {
                val badge = paintSourceBadge(g, card, textLeft)
                localSourceBadges[node.id] = badge
            }

            paintStatusBadge(g, node, card)

            if (node.hasSourceTarget()) {
                paintOpenGlyph(g, card)
            }
        }

        cards = localCards
        sourceBadges = localSourceBadges
    }

    private fun paintDotGrid(g: Graphics2D) {
        g.color = Theme.Dot
        val step = 28
        var y = 18
        while (y < height) {
            var x = 18
            while (x < width) {
                g.fillOval(x, y, 3, 3)
                x += step
            }
            y += step
        }
    }

    private fun updateCanvasSize() {
        val richCards = nodes.any { it.hasRichFacts() }
        val cardW = if (richCards) 292 else 230
        val cardH = if (richCards) 132 else 76
        val gapX = 70
        val gapY = 24
        val startX = 24
        val startY = 46
        val layoutGroups = layoutGroups()
        val columns = layoutGroups.size.coerceAtLeast(1)
        val maxRows = (layoutGroups.maxOfOrNull { it.nodes.size } ?: 1).coerceAtLeast(1)
        val contentWidth = startX + columns * cardW + (columns - 1) * gapX + 24
        val contentHeight = startY + maxRows * cardH + (maxRows - 1) * gapY + 28
        preferredSize = Dimension(
            (contentWidth * zoom).toInt().coerceAtLeast(minimumSize.width),
            (contentHeight * zoom).toInt().coerceAtLeast(minimumSize.height),
        )
    }

    private fun layoutGroups(): List<LayoutGroup> =
        nodes
            .groupBy { it.displayGroupTitle() }
            .map { (title, groupNodes) ->
                LayoutGroup(
                    title = title,
                    order = groupNodes.minOf { it.displayGroupOrder() },
                    nodes = groupNodes.sortedWith(compareBy<NodeView>({ it.wave }, { it.sourcePath }, { it.title })),
                )
            }
            .sortedWith(compareBy<LayoutGroup>({ it.order }, { it.title }))

    private data class LayoutGroup(
        val title: String,
        val order: Int,
        val nodes: List<NodeView>,
    )

    private fun nodeAt(point: Point): NodeView? {
        val scaled = Point((point.x / zoom).toInt(), (point.y / zoom).toInt())
        val id = cards.entries.firstOrNull { (_, card) -> card.contains(scaled) }?.key ?: return null
        return nodes.firstOrNull { it.id == id }
    }

    private fun sourceBadgeAt(point: Point): NodeView? {
        val scaled = Point((point.x / zoom).toInt(), (point.y / zoom).toInt())
        val id = sourceBadges.entries.firstOrNull { (_, badge) -> badge.contains(scaled) }?.key ?: return null
        return nodes.firstOrNull { it.id == id }
    }

    private fun paintSourceBadge(g: Graphics2D, card: RoundRectangle2D.Float, textLeft: Int): RoundRectangle2D.Float {
        val label = "file"
        val width = 36
        val x = textLeft
        val y = (card.y + card.height - 22).toInt()
        val badge = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), width.toFloat(), 16f, 8f, 8f)
        g.color = Theme.AccentSurface
        g.fill(badge)
        g.color = Theme.Accent
        g.font = font.deriveFont(Font.PLAIN, 10f)
        g.drawString(label, x + 8, y + 12)
        return badge
    }

    private fun paintOpenGlyph(g: Graphics2D, card: RoundRectangle2D.Float) {
        val x = (card.x + card.width - 28).toInt()
        val y = (card.y + 10).toInt()
        g.color = Theme.Accent
        g.drawRect(x, y + 4, 10, 10)
        g.drawLine(x + 6, y, x + 16, y)
        g.drawLine(x + 16, y, x + 16, y + 10)
        g.drawLine(x + 10, y + 2, x + 16, y)
        g.drawLine(x + 16, y, x + 14, y + 6)
    }

    private fun paintStatusBadge(g: Graphics2D, node: NodeView, card: RoundRectangle2D.Float) {
        val label = when {
            node.origin == NodeOrigin.CODE && node.status == "CODE" -> "code"
            node.origin == NodeOrigin.PROPOSED_UML -> "proposal"
            else -> node.status.lowercase()
        }.ellipsize(12)
        val width = (label.length * 7 + 14).coerceAtLeast(42)
        val x = (card.x + card.width - width - 10).toInt()
        val y = (card.y + card.height - 22).toInt()
        val pill = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), width.toFloat(), 16f, 8f, 8f)
        g.color = badgeFill(node)
        g.fill(pill)
        g.color = badgeText(node)
        g.font = font.deriveFont(Font.PLAIN, 10f)
        g.drawString(label, x + 7, y + 12)
    }

    private fun paintPill(
        g: Graphics2D,
        text: String,
        x: Int,
        y: Int,
        fill: Color,
        textColor: Color,
    ) {
        val safeText = text.ellipsize(12)
        val width = (safeText.length * 7 + 14).coerceAtLeast(42)
        val pill = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), width.toFloat(), 16f, 8f, 8f)
        g.color = fill
        g.fill(pill)
        g.color = textColor
        g.font = font.deriveFont(Font.PLAIN, 10f)
        g.drawString(safeText, x + 7, y + 12)
    }

    private fun colorFor(node: NodeView): Color {
        if (node.blocked || node.status == "BLOCKED") return Theme.DangerSurface
        return when (node.origin) {
            NodeOrigin.CODE -> Theme.Surface
            NodeOrigin.PROPOSED_UML -> Theme.PurpleSurface
            NodeOrigin.WORKFLOW -> when (node.status) {
                "APPLIED" -> Theme.SuccessSurface
                "REVIEWED" -> Theme.PurpleSurface
                "EXECUTED" -> Theme.AccentSurface
                "PLANNED" -> Theme.WarningSurface
                "PARTIAL" -> Theme.WarningSurface
                else -> if (node.ready) Theme.SuccessSurface else Theme.SurfaceSoft
            }
        }
    }

    private fun borderFor(node: NodeView): Color {
        if (node.blocked || node.status == "BLOCKED") return Theme.Danger
        return when (node.origin) {
            NodeOrigin.CODE -> Theme.Accent
            NodeOrigin.PROPOSED_UML -> Theme.Warning
            NodeOrigin.WORKFLOW -> when (node.status) {
                "APPLIED" -> Theme.Success
                "PARTIAL" -> Theme.Warning
                "REVIEWED" -> Theme.Accent
                "EXECUTED" -> Theme.Accent
                "PLANNED" -> Theme.Warning
                else -> if (node.ready) Theme.Success else Theme.Border
            }
        }
    }

    private fun badgeFill(node: NodeView): Color =
        when {
            node.blocked || node.status == "BLOCKED" -> Theme.Danger
            node.origin == NodeOrigin.CODE -> Theme.AccentSurface
            node.origin == NodeOrigin.PROPOSED_UML -> Theme.WarningSurface
            node.status == "APPLIED" -> Theme.SuccessSurface
            node.status == "PLANNED" || node.status == "PARTIAL" -> Theme.WarningSurface
            else -> Theme.SurfaceSoft
        }

    private fun badgeText(node: NodeView): Color =
        when {
            node.blocked || node.status == "BLOCKED" -> Theme.TextStrong
            node.origin == NodeOrigin.CODE -> Theme.Accent
            node.origin == NodeOrigin.PROPOSED_UML -> Theme.Warning
            else -> Theme.TextStrong
        }

    private fun kindBadgeFill(node: NodeView): Color =
        when (node.origin) {
            NodeOrigin.CODE -> Theme.SurfaceSoft
            NodeOrigin.PROPOSED_UML -> Theme.SurfaceSoft
            NodeOrigin.WORKFLOW -> Theme.SurfaceSoft
        }

    private fun kindBadgeText(node: NodeView): Color =
        when (node.origin) {
            NodeOrigin.CODE -> Theme.Accent
            NodeOrigin.PROPOSED_UML -> Theme.Warning
            NodeOrigin.WORKFLOW -> Theme.TextStrong
        }

    private fun NodeView.metadataLine(): String =
        when (origin) {
            NodeOrigin.CODE -> listOf(kind.ifBlank { "code" }, source.ifBlank { "source: not mapped" }).joinToString("  ")
            NodeOrigin.PROPOSED_UML -> listOf(kind.ifBlank { "UML proposal" }, "proposed, not mapped").joinToString("  ")
            NodeOrigin.WORKFLOW -> listOf(
                kind.ifBlank { "workflow" },
                source.ifBlank { "generated, not mapped" },
                id.take(8),
            ).joinToString("  ")
        }

    private fun NodeView.summaryLine(label: String, values: List<String>, overflow: Int): String {
        if (values.isEmpty()) return "$label: -"
        val suffix = if (overflow > 0) ", +$overflow" else ""
        return "$label: ${values.joinToString(", ")}$suffix"
    }

    private fun NodeView.hasRichFacts(): Boolean =
        source.isNotBlank() || fields.isNotEmpty() || methods.isNotEmpty() || relationshipHint.isNotBlank()

    private fun NodeView.hasSourceTarget(): Boolean =
        sourcePath.isNotBlank()

    private fun NodeView.displayGroupTitle(): String =
        groupTitle.ifBlank { "Wave $wave" }

    private fun NodeView.displayGroupOrder(): Int =
        groupOrder.takeIf { it != 0 } ?: wave

    private fun NodeView.sourceDescription(): String =
        when {
            source.isNotBlank() -> source
            sourcePath.isNotBlank() && sourceLine != null -> "$sourcePath:$sourceLine"
            sourcePath.isNotBlank() -> sourcePath
            origin == NodeOrigin.PROPOSED_UML -> "proposed UML, not mapped to source yet"
            origin == NodeOrigin.WORKFLOW -> "generated workflow node, not mapped to source yet"
            else -> "not mapped to source"
        }

    private fun NodeOrigin.label(): String =
        when (this) {
            NodeOrigin.CODE -> "Current code"
            NodeOrigin.PROPOSED_UML -> "Proposed UML"
            NodeOrigin.WORKFLOW -> "Implementation workflow"
        }

    private fun String.ellipsize(max: Int): String =
        if (length <= max) this else take((max - 3).coerceAtLeast(0)) + "..."

    private fun String.ellipsizeToWidth(g: Graphics2D, maxWidth: Int): String {
        if (isEmpty()) return this
        val metrics = g.fontMetrics
        if (metrics.stringWidth(this) <= maxWidth) return this
        var value = this
        while (value.length > 1 && metrics.stringWidth("$value...") > maxWidth) {
            value = value.dropLast(1)
        }
        return if (value.length == length) value else "$value..."
    }

    private fun escape(text: String): String =
        buildString {
            for (c in text) {
                when (c) {
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    else -> append(c)
                }
            }
        }
}
