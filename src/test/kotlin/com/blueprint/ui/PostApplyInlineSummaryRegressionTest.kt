package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyInlineSummaryRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `successful apply stores inline summary state for review panel`() {
        assertTrue(source.contains("private var postApplyInlineSummary: PostApplyInlineSummary? = null"))
        assertTrue(source.contains("postApplyInlineSummary = PostApplyInlineSummary("))
        assertTrue(source.contains("val validationAndPathsLine = buildString {"))
        assertTrue(source.contains("appendLine(result.summaryLine())"))
        assertTrue(source.contains("append(changedFilesText)"))
        assertTrue(source.contains("validationAndPathsLine = validationAndPathsLine"))
        assertTrue(source.contains("nextStepLine = refreshNote"))
    }

    @Test
    fun `review summary switches to inline applied summary when apply succeeds`() {
        assertTrue(source.contains("reviewSummaryArea.text = if (n.executionStatus == ExecutionStatus.APPLIED && inlineSummary != null) {"))
        assertTrue(source.contains("postApplyReviewSummary(exec, inlineSummary)"))
        assertTrue(source.contains("appendLine(summary.validationAndPathsLine)"))
        assertTrue(source.contains("appendLine(summary.verifyChecklist)"))
        assertTrue(source.contains("append(\"\\nRefresh UML From Code lets you rerun the UML reload when you want an explicit verification step. Then run the changed app to confirm the feature exists. Open Changed Files remains available if you want to inspect what Blueprint wrote after verification.\")"))
    }
}
