package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MiniGraphPanelSourceBadgeCopyTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/MiniGraphPanel.kt")

    @Test
    fun `source badge copy is explicit and not a run affordance`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val label = \"source\""))
        assertTrue(source.contains("Open source file for this code-backed card. This does not run Generate Code Diff."))
        assertTrue(source.contains("Use the source file badge or double-click to open the source file. This does not run Generate Code Diff."))
    }
}
