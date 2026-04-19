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
        assertTrue(source.contains("private var postApplyHighlightMessage: String? = null"))
        assertTrue(source.contains("postApplyChangedPaths = changedPaths"))
        assertTrue(source.contains("postApplyHighlightMessage = null"))
        assertTrue(source.contains("focusChangedEntityAfterRefresh()"))
        assertTrue(source.contains("private fun focusChangedEntityAfterRefresh()"))
        assertTrue(source.contains("val match = changedPaths.firstNotNullOfOrNull { changedPath ->"))
        assertTrue(source.contains("selectedCanvasId = matched.id"))
        assertTrue(source.contains("postApplyHighlightMessage = \"Blueprint highlighted "))
        assertTrue(source.contains("status(\"Refreshed UML and highlighted "))
        assertTrue(source.contains("val umlRefreshLine = \"Code-backed UML was refreshed from disk after apply.\""))
        assertTrue(source.contains("val highlightLine = postApplyHighlightMessage ?: \"Blueprint refreshed the code-backed UML after apply.\""))
    }

    @Test
    fun `refresh after apply reports when no changed UML entity could be highlighted`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("if (match == null) {"))
        assertTrue(source.contains("postApplyHighlightMessage = \"Blueprint refreshed the code-backed UML after apply, but did not find a matching UML entity to highlight from the changed paths.\""))
        assertTrue(source.contains("logActivity(\"Refreshed UML after apply but did not find a changed entity to highlight.\")"))
    }
}
