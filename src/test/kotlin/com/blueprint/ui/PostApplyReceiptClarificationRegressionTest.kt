package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyReceiptClarificationRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `post apply receipt distinguishes automatic refresh from explicit verify`() {
        assertTrue(source.contains("val refreshNote = \"Blueprint already refreshed the code-backed UML from disk after apply. Use Verify In UML to rerun that reread when you want an explicit verification click, or use Refresh UML From Code again later.\""))
        assertTrue(source.contains("val umlRefreshLine = \"Blueprint automatically refreshed the code-backed UML from disk after apply.\""))
        assertTrue(source.contains("appendLine(\"- Automatic refresh after apply already reread the changed code from disk.\")"))
        assertTrue(source.contains("Verify In UML reruns that code reread when you want an explicit verification click. Refresh UML From Code stays available for the general refresh action."))
        assertTrue(source.contains("Blueprint already refreshed the code-backed UML from disk after apply. Use Verify In UML to rerun that reread when you want an explicit verification click, or use Refresh UML From Code again later."))
    }
}
