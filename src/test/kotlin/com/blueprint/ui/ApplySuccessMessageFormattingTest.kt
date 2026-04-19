package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ApplySuccessMessageFormattingTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `apply completion dialog and inline receipt format changed paths and validation cleanly`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("appendLine(\"Changed paths:\")"))
        assertTrue(source.contains("changedPaths.forEach { appendLine(\"- \$it\") }"))
        assertTrue(source.contains("appendLine(\"Validation:\")"))
        assertTrue(source.contains("appendLine(validationReportText(result))"))
        assertTrue(source.contains("val validationAndPathsLine = buildString {"))
        assertTrue(source.contains("appendLine(result.summaryLine())"))
        assertTrue(source.contains("append(changedFilesText)"))
        assertFalse(source.contains("append(validationReportText(result))"))
    }

    @Test
    fun `apply completion copy points to refresh and avoids fake semantic counts`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val refreshNote = \"Blueprint already refreshed the code-backed UML from disk after apply. Refresh UML From Code to verify again whenever you want to rerun that reread.\""))
        assertTrue(source.contains("val undoNote = if (undoLastApplyButton.isEnabled)"))
        assertTrue(source.contains("val umlRefreshLine = \"Blueprint automatically refreshed the code-backed UML from disk after apply.\""))
        assertTrue(source.contains("val highlightLine = postApplyHighlightMessage ?: \"Blueprint refreshed the code-backed UML after apply.\""))
        assertTrue(source.contains("val whatChanged = PatchChangeSummary.applySummary(registry.getExecution(node.id), changedPaths)"))
        assertTrue(source.contains("listOf(summaryLine, whatChanged, changedPathsBlock, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, highlightLine, validationBlock)"))
        assertFalse(source.contains("Applied semantic changes:"))
        assertFalse(source.contains("fields changed"))
    }
}
