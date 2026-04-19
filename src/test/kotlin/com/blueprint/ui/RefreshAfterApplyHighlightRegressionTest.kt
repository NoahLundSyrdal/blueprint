package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RefreshAfterApplyHighlightRegressionTest {
    @Test
    fun `refresh after apply remembers changed paths and highlights the matching UML entity`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("private var postApplyChangedPaths: List<String> = emptyList()"))
        assertTrue(source.contains("postApplyChangedPaths = changedPaths"))
        assertTrue(source.contains("focusChangedEntityAfterRefresh()"))
        assertTrue(source.contains("private fun focusChangedEntityAfterRefresh()"))
        assertTrue(source.contains("selectedCanvasId = matched.id"))
        assertTrue(source.contains("status(\"Refreshed UML and highlighted "))
        assertTrue(source.contains("val umlRefreshLine = \"Code-backed UML was refreshed from disk after apply.\""))
        assertTrue(source.contains("val highlightLine = \"Blueprint highlighted the best-matching changed entity when it could.\""))
    }
}
