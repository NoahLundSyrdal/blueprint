package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ApplyVerificationCopyRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `apply success and validation failure copy show exact verification details`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("appendLine(\"Validation:\")"))
        assertTrue(source.contains("appendLine(validationReportText(result))"))
        assertTrue(source.contains("result.detailLabel()"))
        assertTrue(source.contains("command: "))
        assertTrue(source.contains("result.command.ifBlank { \"not available\" }"))
        assertTrue(source.contains("Result: "))
        assertTrue(source.contains("result.reason"))
        assertTrue(source.contains("append(\"\\n\\nOutput excerpt:\\n\")"))
        assertTrue(source.contains("Messages.showWarningDialog("))
        assertTrue(source.contains("validationReportText(result),"))
        assertTrue(source.contains("val runNote = inferredRunNote()"))
        assertTrue(source.contains("listOf(summaryLine, whatChanged, changedPathsBlock, refreshNote, runNote, undoNote, umlRefreshLine, highlightLine, validationBlock)"))
    }
}
