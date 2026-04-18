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
    )

    private var nodes: List<NodeView> = emptyList()
    private var cards: Map<String, RoundRectangle2D.Float> = emptyMap()
    var onNodeSelected: ((String) -> Unit)? = null

    init {
        preferredSize = Dimension(900, 420)
        minimumSize = Dimension(520, 300)
        background = Theme.Background
        toolTipText = ""
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                nodeAt(e.point)?.let { node ->
                    onNodeSelected?.invoke(node.id)
                    repaint()
                }
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                cursor = if (nodeAt(e.point) != null) {
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    Cursor.getDefaultCursor()
                }
            }
        })
    }

    fun setGraph(newNodes: List<NodeView>) {
        nodes = newNodes
        repaint()
    }

    override fun getToolTipText(event: MouseEvent): String? =
        nodeAt(event.point)?.let { node ->
            "<html><b>${escape(node.title)}</b><br/>" +
                "ID: ${node.id.take(8)}<br/>" +
                "Status: ${node.status}<br/>" +
                "Wave: ${node.wave}<br/>" +
                escape(node.detail).replace("\n", "<br/>") +
                "</html>"
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
        if (nodes.isEmpty()) {
            g.color = Theme.Muted
            g.font = font.deriveFont(Font.PLAIN, 13f)
            g.drawString("Click Abstract Code to UML to draw this project as architecture.", 24, 38)
            cards = emptyMap()
            return
        }

        val waves = nodes.groupBy { it.wave }.toSortedMap()
        val cardW = 190f
        val cardH = 60f
        val gapX = 70f
        val gapY = 24f
        val startX = 24f
        val startY = 46f
        val localCards = mutableMapOf<String, RoundRectangle2D.Float>()

        waves.entries.forEachIndexed { waveIndex, (wave, waveNodes) ->
            val x = startX + waveIndex * (cardW + gapX)
            g.color = Theme.TextStrong
            g.font = font.deriveFont(Font.BOLD, 12f)
            g.drawString("Wave $wave", x.toInt(), 28)
            waveNodes.forEachIndexed { row, node ->
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
            val fill = colorFor(node.status, node.ready, node.blocked)
            g.color = fill
            g.fill(card)
            g.color = if (node.selected) Theme.Accent else borderFor(node.status, node.ready, node.blocked)
            g.stroke = BasicStroke(if (node.selected) 3.0f else 1.3f)
            g.draw(card)

            g.color = Theme.TextStrong
            g.font = font.deriveFont(Font.BOLD, 12f)
            g.drawString(node.title.take(25), (card.x + 12).toInt(), (card.y + 23).toInt())
            g.font = font.deriveFont(Font.PLAIN, 11f)
            g.color = Theme.Muted
            g.drawString("${node.status}  ${node.id.take(8)}", (card.x + 12).toInt(), (card.y + 45).toInt())

            g.color = if (node.selected) Theme.Accent else Theme.Muted
            g.drawOval((card.x + card.width - 28).toInt(), (card.y + 10).toInt(), 16, 16)
            val px = (card.x + card.width - 22).toInt()
            val py = (card.y + 14).toInt()
            g.fillPolygon(intArrayOf(px, px, px + 7), intArrayOf(py, py + 8, py + 4), 3)
        }

        cards = localCards
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

    private fun nodeAt(point: Point): NodeView? {
        val id = cards.entries.firstOrNull { (_, card) -> card.contains(point) }?.key ?: return null
        return nodes.firstOrNull { it.id == id }
    }

    private fun colorFor(status: String, ready: Boolean, blocked: Boolean): Color {
        if (blocked || status == "BLOCKED") return Theme.DangerSurface
        return when (status) {
            "UML" -> Theme.Surface
            "APPLIED" -> Theme.SuccessSurface
            "REVIEWED" -> Theme.PurpleSurface
            "EXECUTED" -> Theme.AccentSurface
            "PLANNED" -> Theme.WarningSurface
            "PARTIAL" -> Theme.WarningSurface
            else -> if (ready) Theme.SuccessSurface else Theme.SurfaceSoft
        }
    }

    private fun borderFor(status: String, ready: Boolean, blocked: Boolean): Color {
        if (blocked || status == "BLOCKED") return Theme.Danger
        return when (status) {
            "UML" -> Theme.Border
            "APPLIED" -> Theme.Success
            "PARTIAL" -> Theme.Warning
            "REVIEWED" -> Theme.Accent
            "EXECUTED" -> Theme.Accent
            "PLANNED" -> Theme.Warning
            else -> if (ready) Theme.Success else Theme.Border
        }
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
