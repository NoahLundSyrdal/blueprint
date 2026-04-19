package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ScopeReceiptSkippedPathsRegressionTest {
    @Test
    fun `scope receipt highlights skipped paths when refresh skips files`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("- Scope note: Some Python paths were skipped during Refresh UML From Code."))
        assertTrue(source.contains("- Confidence: the current UML still reflects the Python files Blueprint could read."))
        assertTrue(source.contains("- Next action: inspect the skipped paths below, fix the folder or files if needed, then Refresh UML From Code again before Generate Code Diff."))
        assertTrue(source.contains("Some Python paths were skipped during Refresh UML From Code. Blueprint still built the current UML from the Python files it could read. If anything looks incomplete, inspect these skipped paths, fix the folder or files if needed, then Refresh UML From Code again before Generate Code Diff:"))
    }
}
