package com.blueprint.ui

import java.awt.image.BufferedImage
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
