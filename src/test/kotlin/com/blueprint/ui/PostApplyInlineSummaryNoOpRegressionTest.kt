package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyInlineSummaryNoOpRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `inline post apply receipt keeps validation with no changed paths`() {
        assertTrue(source.contains("val changedFilesText = if (changedPaths.isEmpty()) {"))
        assertTrue(source.contains("\"No changed paths were written.\""))
        assertTrue(source.contains("val validationAndPathsLine = buildString {"))
        assertTrue(source.contains("appendLine(result.summaryLine())"))
        assertTrue(source.contains("append(changedFilesText)"))
        assertTrue(source.contains("appendLine(summary.validationAndPathsLine)"))
    }
}
