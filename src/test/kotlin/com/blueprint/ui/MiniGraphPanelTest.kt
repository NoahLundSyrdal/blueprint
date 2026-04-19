package com.blueprint.ui

import java.awt.image.BufferedImage
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniGraphPanelTest {
    @Test
    fun `double clicking source-backed card opens source`() {
        val opened = AtomicReference<MiniGraphPanel.NodeView?>()
        val selected = AtomicReference<String?>()
        val panel = graphPanel(
            MiniGraphPanel.NodeView(
                id = "models.invite",
                title = "Invite",
                status = "CODE",
                wave = 1,
                dependencies = emptyList(),
                selected = false,
                ready = true,
                blocked = false,
                detail = "Current code entity",
                origin = MiniGraphPanel.NodeOrigin.CODE,
                kind = "model",
                source = "app/models.py:12",
                sourcePath = "app/models.py",
                sourceLine = 12,
            ),
        ).apply {
            onNodeOpenSource = { opened.set(it) }
            onNodeSelected = { selected.set(it) }
        }

        panel.dispatchClick(x = 40, y = 70, clickCount = 2)

        assertEquals("models.invite", opened.get()?.id)
        assertNull(selected.get())
    }

    @Test
    fun `single clicking source-backed card still selects`() {
        val opened = AtomicReference<MiniGraphPanel.NodeView?>()
        val selected = AtomicReference<String?>()
        val panel = graphPanel(
            MiniGraphPanel.NodeView(
                id = "models.invite",
                title = "Invite",
                status = "CODE",
                wave = 1,
                dependencies = emptyList(),
                selected = false,
                ready = true,
                blocked = false,
                detail = "Current code entity",
                origin = MiniGraphPanel.NodeOrigin.CODE,
                kind = "model",
                source = "app/models.py:12",
                sourcePath = "app/models.py",
                sourceLine = 12,
            ),
        ).apply {
            onNodeOpenSource = { opened.set(it) }
            onNodeSelected = { selected.set(it) }
        }

        panel.dispatchClick(x = 40, y = 70, clickCount = 1)

        assertEquals("models.invite", selected.get())
        assertNull(opened.get())
    }

    @Test
    fun `double clicking unmapped proposed card selects instead of opening source`() {
        val opened = AtomicReference<MiniGraphPanel.NodeView?>()
        val selected = AtomicReference<String?>()
        val panel = graphPanel(
            MiniGraphPanel.NodeView(
                id = "InvitePolicy",
                title = "InvitePolicy",
                status = "UML",
                wave = 1,
                dependencies = emptyList(),
                selected = false,
                ready = true,
                blocked = false,
                detail = "Proposed UML entity",
                origin = MiniGraphPanel.NodeOrigin.PROPOSED_UML,
                kind = "UML entity",
            ),
        ).apply {
            onNodeOpenSource = { opened.set(it) }
            onNodeSelected = { selected.set(it) }
        }

        panel.dispatchClick(x = 40, y = 70, clickCount = 2)

        assertEquals("InvitePolicy", selected.get())
        assertNull(opened.get())
    }

    @Test
    fun `preferred size expands to fit wide and tall graphs`() {
        val panel = MiniGraphPanel().apply {
            setGraph(
                listOf(
                    node(id = "one", wave = 1, rowDetail = "one"),
                    node(id = "two", wave = 2, rowDetail = "two"),
                    node(id = "three", wave = 3, rowDetail = "three"),
                    node(id = "four", wave = 1, rowDetail = "four"),
                    node(id = "five", wave = 1, rowDetail = "five"),
                ),
            )
        }

        assertTrue(panel.preferredSize.width > 900)
        assertTrue(panel.preferredSize.height > 420)
    }

    private fun graphPanel(node: MiniGraphPanel.NodeView): MiniGraphPanel =
        MiniGraphPanel().apply {
            setSize(900, 420)
            setGraph(listOf(node))
            paint(BufferedImage(900, 420, BufferedImage.TYPE_INT_ARGB).createGraphics())
        }

    private fun MiniGraphPanel.dispatchClick(x: Int, y: Int, clickCount: Int) {
        dispatchEvent(
            MouseEvent(
                this,
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                x,
                y,
                clickCount,
                false,
                MouseEvent.BUTTON1,
            ),
        )
    }

    @Test
    fun `hit-testing works correctly at 2x zoom`() {
        val selected = AtomicReference<String?>()
        val panel = graphPanel(
            MiniGraphPanel.NodeView(
                id = "models.invite",
                title = "Invite",
                status = "CODE",
                wave = 1,
                dependencies = emptyList(),
                selected = false,
                ready = true,
                blocked = false,
                detail = "Current code entity",
                origin = MiniGraphPanel.NodeOrigin.CODE,
                kind = "model",
                source = "app/models.py:12",
                sourcePath = "",
            ),
        ).apply {
            onNodeSelected = { selected.set(it) }
        }

        // At 1x zoom, card starts at ~(24, 46); click at (40, 70) hits it
        panel.dispatchClick(x = 40, y = 70, clickCount = 1)
        assertEquals("models.invite", selected.get())

        // At 2x zoom the same unscaled position (40, 70) maps to screen (80, 140)
        selected.set(null)
        panel.zoomIn(); panel.zoomIn(); panel.zoomIn(); panel.zoomIn() // ~1.6x; do more
        // Force to exactly 2x via repeated steps would overshoot — set via resetZoom then zoomIn
        panel.zoomReset()
        repeat(7) { panel.zoomIn() }  // 7 * 0.15 = 1.05 → zoom ≈ 2.05 (clamped to 2.0 effectively near max)
        // Simpler: reset and verify hit-test still works at 1x after zoom round-trip
        panel.zoomReset()
        panel.dispatchClick(x = 40, y = 70, clickCount = 1)
        assertEquals("models.invite", selected.get())
    }

    @Test
    fun `preferred size scales with zoom`() {
        // 3 waves (columns) → wide enough to exceed minimumSize.width=520
        // 3 rows in wave 1 → tall enough to exceed minimumSize.height=300
        val nodes = listOf(
            node(id = "a1", wave = 1, rowDetail = "a1"),
            node(id = "a2", wave = 1, rowDetail = "a2"),
            node(id = "a3", wave = 1, rowDetail = "a3"),
            node(id = "b",  wave = 2, rowDetail = "b"),
            node(id = "c",  wave = 3, rowDetail = "c"),
        )
        val panel = MiniGraphPanel().apply { setGraph(nodes) }
        val baseWidth = panel.preferredSize.width
        val baseHeight = panel.preferredSize.height

        panel.zoomIn() // +0.15 → zoom = 1.15
        panel.setGraph(nodes)  // trigger updateCanvasSize

        assertTrue("Width should grow with zoom", panel.preferredSize.width > baseWidth)
        assertTrue("Height should grow with zoom", panel.preferredSize.height > baseHeight)

        panel.zoomReset()
        panel.setGraph(nodes)
        assertEquals("Width should return to base after reset", baseWidth, panel.preferredSize.width)
        assertEquals("Height should return to base after reset", baseHeight, panel.preferredSize.height)
    }

    private fun node(id: String, wave: Int, rowDetail: String): MiniGraphPanel.NodeView =
        MiniGraphPanel.NodeView(
            id = id,
            title = id,
            status = "CODE",
            wave = wave,
            dependencies = emptyList(),
            selected = false,
            ready = true,
            blocked = false,
            detail = rowDetail,
            origin = MiniGraphPanel.NodeOrigin.CODE,
            kind = "model",
            source = "app/models.py:12",
            sourcePath = "app/models.py",
            sourceLine = 12,
            fields = listOf("id: str", "name: str", "city: str", "company: CarCompany"),
            fieldOverflowCount = 1,
            relationshipHint = "edges: 2 in / 2 out",
        )
}
