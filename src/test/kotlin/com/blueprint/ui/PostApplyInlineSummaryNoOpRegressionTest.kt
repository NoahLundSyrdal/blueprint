package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyInlineSummaryNoOpRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `inline post apply receipt keeps validation with no changed paths`() {
        assertTrue(source.contains("val writtenPathsText = if (changedPaths.isEmpty()) {"))
        assertTrue(source.contains("\"Written paths: none.\""))
        assertTrue(source.contains("val validationAndPathsLine = buildString {"))
        assertTrue(source.contains("appendLine(result.summaryLine())"))
        assertTrue(source.contains("append(writtenPathsText)"))
        assertTrue(source.contains("appendLine(resultDetailsSection())"))
        assertTrue(source.contains("private fun resultDetailsSection(): String ="))
        assertTrue(source.contains("validationAndPathsLine.removePrefix(receiptSummary).trimStart('\\n')"))
    }
}
