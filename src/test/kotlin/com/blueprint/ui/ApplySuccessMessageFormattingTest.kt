package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ApplySuccessMessageFormattingTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `apply completion dialog uses appendLine for validation block`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("appendLine(\"Changed paths:\")"))
        assertTrue(source.contains("changedPaths.forEach { appendLine(\"- \$it\") }"))
        assertTrue(source.contains("appendLine(\"Validation:\")"))
        assertTrue(source.contains("appendLine(validationReportText(result))"))
        assertTrue(source.contains("appendLine(changedFilesText)"))
        assertFalse(source.contains("append(validationReportText(result))"))
        assertFalse(source.contains("append(changedFilesText)"))
    }

    @Test
    fun `apply completion copy points to refresh and avoids fake semantic counts`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val refreshNote = \"Blueprint already refreshed the code-backed UML from disk after apply. Use Verify In UML to rerun that reread when you want an explicit verification click, or use Refresh UML From Code again later.\""))
        assertTrue(source.contains("val undoNote = if (undoLastApplyButton.isEnabled)"))
        assertTrue(source.contains("val umlRefreshLine = \"Blueprint automatically refreshed the code-backed UML from disk after apply.\""))
        assertTrue(source.contains("val highlightLine = postApplyHighlightMessage ?: \"Blueprint refreshed the code-backed UML after apply.\""))
        assertTrue(source.contains("val whatChanged = PatchChangeSummary.applySummary(registry.getExecution(node.id), changedPaths)"))
        assertTrue(source.contains("listOf(summaryLine, whatChanged, changedPathsBlock, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, highlightLine, validationBlock)"))
        assertFalse(source.contains("Applied semantic changes:"))
        assertFalse(source.contains("fields changed"))
    }
}
