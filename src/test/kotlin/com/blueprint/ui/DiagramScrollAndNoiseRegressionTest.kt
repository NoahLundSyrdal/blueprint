package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class DiagramScrollAndNoiseRegressionTest {
    @Test
    fun `plain mouse wheel scrolls the UML canvas instead of zooming it away`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/MiniGraphPanel.kt"))

        assertTrue(source.contains("if (!e.isControlDown && !e.isMetaDown)"))
        assertTrue(source.contains("scrollPane.verticalScrollBar"))
        assertTrue(source.contains("applyZoom(zoom + delta, pivotX = e.x.toDouble(), pivotY = e.y.toDouble())"))
        assertFalse(source.contains("All vertical scroll events zoom"))
    }

    @Test
    fun `canvas toolbar uses readable zoom controls`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("JButton(\"Fit\")"))
        assertTrue(source.contains("miniGraph.fitToView()"))
        assertTrue(source.contains("JButton(\"1:1\")"))
        assertTrue(source.contains("Reset the UML card zoom to readable size."))
    }

    @Test
    fun `receipt stays compact and demo copy stays out of the main workflow`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("preferredSize = Dimension(0, 190)"))
        assertTrue(source.contains("maximumSize = Dimension(Int.MAX_VALUE, 64)"))
        assertFalse(source.contains("BorderFactory.createTitledBorder(\"First-Run Demo\")"))
    }
}
