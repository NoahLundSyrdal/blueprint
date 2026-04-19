package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyVerifyChecklistRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `apply success stores a verify checklist for the uml tab`() {
        assertTrue(source.contains("val verifyChecklist = buildString {"))
        assertTrue(source.contains("appendLine(\"Refresh UML From Code verification:\")"))
        assertTrue(source.contains("appendLine(\"- \$summaryLine\")"))
        assertTrue(source.contains("appendLine(\"- \${result.summaryLine()}\")"))
        assertTrue(source.contains("appendLine(\"- Changed paths:\")"))
        assertTrue(source.contains("changedPaths.forEach { appendLine(\"  - \$it\") }"))
        assertTrue(source.contains("appendLine(\"- \$umlRefreshLine\")"))
        assertTrue(source.contains("appendLine(\"- \$highlightLine\")"))
        assertTrue(source.contains("appendLine(\"- \$refreshNote\")"))
        assertTrue(source.contains("verifyChecklist = verifyChecklist"))
    }

    @Test
    fun `post apply review summary renders the verify checklist`() {
        assertTrue(source.contains("appendLine(summary.verifyChecklist)"))
        assertTrue(source.contains("val verifyChecklist: String,"))
    }
}
