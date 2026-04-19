package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyVerifyChecklistRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `apply success stores a verify checklist for the uml tab`() {
        assertTrue(source.contains("val verifyChecklist = postApplyVerifyChecklist("))
        assertTrue(source.contains("private fun postApplyVerifyChecklist(summaryLine: String, validationSummary: String, changedPaths: List<String>, verificationSummaryLine: String, refreshNote: String): String ="))
        assertTrue(source.contains("appendLine(POST_APPLY_VERIFY_HEADING)"))
        assertTrue(source.contains("appendLine(\"- \$summaryLine\")"))
        assertTrue(source.contains("appendLine(\"- \$validationSummary\")"))
        assertTrue(source.contains("appendLine(\"- Written paths:\")"))
        assertTrue(source.contains("changedPaths.forEach { appendLine(\"  - \$it\") }"))
        assertTrue(source.contains("appendLine(\"- \$verificationSummaryLine\")"))
        assertTrue(source.contains("appendLine(\"- \$refreshNote\")"))
        assertTrue(source.contains("verifyChecklist = verifyChecklist"))
    }

    @Test
    fun `post apply review summary renders the verify checklist`() {
        assertTrue(source.contains("appendLine(verifyChecklist)"))
        assertTrue(source.contains("val verifyChecklist: String,"))
    }
}
