package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class EmptyStatePartialRefreshRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `empty state surfaces skipped path reasoning before the user generates a diff`() {
        assertTrue(source.contains("val partialRefreshNote = emptyStatePartialRefreshNote(context)"))
        assertTrue(source.contains("partialRefreshNote,"))
        assertTrue(source.contains("prefix = \"Partial refresh note:\""))
        assertTrue(source.contains("skipped during Refresh UML From Code"))
        assertTrue(source.contains("fix the folder or files if needed, then Refresh UML From Code again before Generate Code Diff"))
    }

    @Test
    fun `partial refresh note references actual skipped counts reasons and example paths`() {
        assertTrue(source.contains("private fun skippedPathInlineSummary("))
        assertTrue(source.contains("if (context.skippedFiles.isEmpty()) return \"\""))
        assertTrue(source.contains("val topReasons = context.skippedFiles.groupingBy { it.reason }.eachCount()"))
        assertTrue(source.contains("val examplePaths = context.skippedFiles.take(examplePathLimit).joinToString(\", \") { it.path }"))
        assertTrue(source.contains("\$prefix \${context.skippedFiles.size} Python path\${if (context.skippedFiles.size == 1) \" was\" else \"s were\"}"))
    }
}
