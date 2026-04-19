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
        assertTrue(source.contains("val validationDetailsText = validationReportText(result)"))
        assertTrue(source.contains("appendLine(validationDetailsText)"))
        assertTrue(source.contains("val validationAndPathsLine = buildString {"))
        assertTrue(source.contains("appendLine(validationDetailsText)"))
        assertTrue(source.contains("append(writtenPathsText)"))
        assertFalse(source.contains("append(validationReportText(result))"))
    }

    @Test
    fun `apply completion copy points to refresh and avoids fake semantic counts`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val refreshNote = POST_APPLY_REFRESH_NOTE"))
        assertTrue(source.contains("val undoNote = if (undoLastApplyButton.isEnabled)"))
        assertTrue(source.contains("val highlightLine = postApplyHighlightMessage ?: \"Blueprint refreshed the code-backed UML after apply.\""))
        assertTrue(source.contains("val verificationSummaryLine = if (highlightLine == \"Blueprint refreshed the code-backed UML after apply.\") {"))
        assertTrue(source.contains("val whatChanged = PatchChangeSummary.applySummary(registry.getExecution(node.id), changedPaths)"))
        assertTrue(source.contains("listOf(nextActionLine, summaryLine, whatChanged, changedPathsBlock, commandBlock, refreshNote, verificationSummaryLine, runNote, undoNote, validationBlock)"))
        assertFalse(source.contains("Applied semantic changes:"))
        assertFalse(source.contains("fields changed"))
    }
}
