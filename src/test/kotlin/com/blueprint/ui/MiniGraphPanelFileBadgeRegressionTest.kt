package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MiniGraphPanelFileBadgeRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/MiniGraphPanel.kt")

    @Test
    fun `source badge uses file wording instead of a play style affordance`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val label = \"file\""))
        assertTrue(source.contains("Use the file badge or double-click to open the source file. This does not run Generate Code Diff or Apply Approved Changes."))
        assertTrue(source.contains("Open the source file for this code-backed card. This does not run Generate Code Diff or Apply Approved Changes."))
        assertFalse(source.contains("val label = \"open\""))
        assertFalse(source.contains("Use the open file badge or double-click to open the source file."))
        assertFalse(source.contains("Open source file for this code-backed card."))
    }
}
