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

        assertTrue(source.contains("val summaryLine = when {"))
        assertTrue(source.contains("changedPaths.isEmpty() ->"))
        assertTrue(source.contains("\"No code changes needed. Validation passed.\""))
        assertTrue(source.contains("\"No code changes needed. Validation skipped because no command was inferred.\""))
        assertTrue(source.contains("\"No code changes needed. Validation skipped.\""))
        assertTrue(source.contains("\"No code changes needed. Validation failed.\""))
        assertTrue(source.contains("else -> buildString {"))
        assertTrue(source.contains("val changedPathsBlock = buildString {"))
        assertTrue(source.contains("appendLine(\"Changed paths:\")"))
        assertTrue(source.contains("changedPaths.forEach { appendLine(\"- \$it\") }"))
        assertTrue(source.contains("val writtenPathCountLine = if (changedPaths.isEmpty()) {"))
        assertTrue(source.contains("\"Written paths: none.\""))
        assertTrue(source.contains("\"Written paths (\${changedPaths.size}): \${changedPaths.joinToString(\", \")}\""))
        assertTrue(source.contains("val validationOutcomeLine = when (result.status) {"))
        assertTrue(source.contains("\"Validation outcome: passed.\""))
        assertTrue(source.contains("\"Validation outcome: skipped because no command was inferred.\""))
        assertTrue(source.contains("\"Validation outcome: failed.\""))
        assertTrue(source.contains("val validationBlock = buildString {"))
        assertTrue(source.contains("appendLine(\"Validation:\")"))
        assertTrue(source.contains("appendLine(\"Run after apply:\")"))
        assertTrue(source.contains("appendLine(validationReportText(result))"))
        assertTrue(source.contains("val undoNote = if (undoLastApplyButton.isEnabled)"))
        assertTrue(source.contains("Undo Last Apply is available if you want to roll back this reviewed code patch."))
        assertTrue(source.contains("val commandBlock = commandReviewBlock(validationCommand, pythonContext)"))
        assertTrue(source.contains("listOf(nextActionLine, summaryLine, whatChanged, changedPathsBlock, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, verifyStateLine, highlightLine, validationBlock)"))
        assertTrue(source.contains("val receiptSummary = buildString {"))
        assertTrue(source.contains("appendLine(\"Apply receipt:\")"))
        assertTrue(source.contains("status(summaryLine)"))
        assertTrue(source.contains("ProjectValidationService.ValidationResult.Status.SKIPPED -> {"))
        assertTrue(source.contains("Validation skipped because no command was inferred."))
        assertTrue(source.contains("postApplyVerifyState = \"Blueprint automatically refreshed the code-backed UML from disk after apply.\""))
        assertTrue(source.contains("guideLabel.text = listOf("))
        assertTrue(source.contains("\"Next: Refresh UML From Code to verify the updated code-backed UML.\""))
        assertTrue(source.contains("summaryLine,"))
        assertTrue(source.contains("verifyStateLine,"))
        assertTrue(source.contains("\"Refresh UML From Code verifies the updated code-backed UML again whenever you want to confirm it yourself.\""))
        assertTrue(source.contains("\"Use Undo Last Apply to roll back this reviewed code patch.\""))
        assertTrue(source.contains("appendChat("))
        assertTrue(source.contains("listOf(nextActionLine, summaryLine, whatChanged, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, verifyStateLine, highlightLine)"))
    }
}
