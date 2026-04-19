package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class DiagramCanvasVisibilityRegressionTest {
    @Test
    fun `diagram header does not consume the graph viewport`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertFalse(source.contains("add(JPanel(GridLayout(0, 1, 2, 2)).apply"))
        assertTrue(source.contains("layout = BoxLayout(this, BoxLayout.Y_AXIS)"))
        assertTrue(source.contains("maximumSize = Dimension(Int.MAX_VALUE, 96)"))
        assertTrue(source.contains("add(JBScrollPane(miniGraph).apply"))
    }
}
