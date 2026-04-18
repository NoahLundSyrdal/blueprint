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
        preferredSize = Dimension(760, 300)
        minimumSize = Dimension(440, 220)
        background = Color(0xFAFAFA)
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
        if (nodes.isEmpty()) {
            g.color = Color(0x777777)
            g.font = font.deriveFont(Font.PLAIN, 13f)
            g.drawString("No graph yet. Click Generate UML or Seed: UML Invite Flow.", 18, 32)
            cards = emptyMap()
            return
        }

        val waves = nodes.groupBy { it.wave }.toSortedMap()
        val cardW = 178f
        val cardH = 54f
        val gapX = 58f
        val gapY = 20f
        val startX = 18f
        val startY = 38f
        val localCards = mutableMapOf<String, RoundRectangle2D.Float>()

        waves.entries.forEachIndexed { waveIndex, (wave, waveNodes) ->
            val x = startX + waveIndex * (cardW + gapX)
            g.color = Color(0x666666)
            g.font = font.deriveFont(Font.BOLD, 12f)
            g.drawString("Wave $wave", x.toInt(), 24)
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
                g.color = Color(0x9AA0A6)
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
            g.color = if (node.selected) Color(0x1A73E8) else borderFor(node.status, node.ready, node.blocked)
            g.stroke = BasicStroke(if (node.selected) 3.0f else 1.3f)
            g.draw(card)

            g.color = Color(0x1F1F1F)
            g.font = font.deriveFont(Font.BOLD, 12f)
            g.drawString(node.title.take(24), (card.x + 10).toInt(), (card.y + 20).toInt())
            g.font = font.deriveFont(Font.PLAIN, 11f)
            g.color = Color(0x333333)
            g.drawString("${node.status}  ${node.id.take(8)}", (card.x + 10).toInt(), (card.y + 40).toInt())
        }

        cards = localCards
    }

    private fun nodeAt(point: Point): NodeView? {
        val id = cards.entries.firstOrNull { (_, card) -> card.contains(point) }?.key ?: return null
        return nodes.firstOrNull { it.id == id }
    }

    private fun colorFor(status: String, ready: Boolean, blocked: Boolean): Color {
        if (blocked || status == "BLOCKED") return Color(0xFFE2D6)
        return when (status) {
            "APPLIED" -> Color(0xD6F4E2)
            "REVIEWED" -> Color(0xE3EDFF)
            "EXECUTED" -> Color(0xEEF3FF)
            "PLANNED" -> Color(0xFFF4D6)
            "PARTIAL" -> Color(0xFFEAC2)
            else -> if (ready) Color(0xE6F4EA) else Color(0xF1F3F4)
        }
    }

    private fun borderFor(status: String, ready: Boolean, blocked: Boolean): Color {
        if (blocked || status == "BLOCKED") return Color(0xD56B45)
        return when (status) {
            "APPLIED" -> Color(0x2E7D32)
            "PARTIAL" -> Color(0xC77800)
            "REVIEWED" -> Color(0x557BD8)
            "EXECUTED" -> Color(0x78909C)
            "PLANNED" -> Color(0xD6A000)
            else -> if (ready) Color(0x34A853) else Color(0xD0D0D0)
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
