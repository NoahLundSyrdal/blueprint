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
        assertTrue(source.contains("- If the UML looks incomplete or you need higher confidence, inspect the skipped paths below."))
        assertTrue(source.contains("Some Python paths were skipped during Refresh UML From Code. The current UML still reflects the Python files Blueprint could read. If the UML looks incomplete or you need higher confidence, inspect these skipped paths:"))
    }
}
