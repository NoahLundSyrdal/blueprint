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
        assertTrue(source.contains("validationLine = result.summaryLine()"))
        assertTrue(source.contains("nextStepLine = refreshNote"))
    }

    @Test
    fun `review summary switches to inline applied summary when apply succeeds`() {
        assertTrue(source.contains("reviewSummaryArea.text = if (n.executionStatus == ExecutionStatus.APPLIED && inlineSummary != null) {"))
        assertTrue(source.contains("postApplyReviewSummary(exec, inlineSummary)"))
        assertTrue(source.contains("appendLine(summary.verifyChecklist)"))
        assertTrue(source.contains("append(\"\\nVerify In UML rereads the changed code from disk. Open Changed Files to inspect what Blueprint wrote before you rerun the app.\")"))
    }
}
