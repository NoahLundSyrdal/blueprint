package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ApplySuccessCopyRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `apply success message keeps verification steps clear and compile safe`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val changedPathsBlock = buildString {"))
        assertTrue(source.contains("appendLine(\"Changed paths:\")"))
        assertTrue(source.contains("changedPaths.forEach { appendLine(\"- \$it\") }"))
        assertTrue(source.contains("val validationBlock = buildString {"))
        assertTrue(source.contains("appendLine(\"Validation:\")"))
        assertTrue(source.contains("appendLine(validationReportText(result))"))
        assertTrue(source.contains("val undoNote = if (undoLastApplyButton.isEnabled)"))
        assertTrue(source.contains("Undo Last Apply is available if you want to roll back this reviewed code patch."))
        assertTrue(source.contains("val commandBlock = commandReviewBlock(validationCommand, pythonContext)"))
        assertTrue(source.contains("listOf(summaryLine, whatChanged, changedPathsBlock, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, highlightLine, validationBlock)"))
        assertTrue(source.contains("status(summaryLine)"))
        assertTrue(source.contains("ProjectValidationService.ValidationResult.Status.SKIPPED -> {"))
        assertTrue(source.contains("Validation skipped because no command was inferred."))
        assertTrue(source.contains("guideLabel.text = listOf("))
        assertTrue(source.contains("\"Apply complete. Refresh UML From Code to verify.\""))
        assertTrue(source.contains("\"Use Undo Last Apply to roll back this reviewed code patch.\""))
        assertTrue(source.contains("appendChat("))
        assertTrue(source.contains("listOf(summaryLine, whatChanged, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, highlightLine)"))
    }
}
