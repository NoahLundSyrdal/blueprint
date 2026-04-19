package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyReceiptPersistenceRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `post apply summary stays visible until a new diff or non-apply refresh replaces it`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val inlineSummary = postApplyInlineSummary"))
        assertTrue(source.contains("if (n.executionStatus == ExecutionStatus.APPLIED && inlineSummary != null)"))
        assertTrue(source.contains("private fun postApplyReviewSummary(exec: ExecutionArtifact?, summary: PostApplyInlineSummary): String ="))
        assertTrue(source.contains("appendLine(\"Changed paths:\")"))
        assertTrue(source.contains("appendLine(summary.validationLine)"))
        assertTrue(source.contains("append(summary.nextStepLine)"))
        assertTrue(source.contains("postApplyInlineSummary = null"))
        assertTrue(source.contains("if (!refreshedAfterApply) {"))
    }
}
