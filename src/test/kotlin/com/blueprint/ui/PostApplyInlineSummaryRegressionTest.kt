package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyInlineSummaryRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `successful apply stores concise inline result summary plus separate next step`() {
        assertTrue(source.contains("private var postApplyInlineSummary: PostApplyInlineSummary? = null"))
        assertTrue(source.contains("postApplyInlineSummary = PostApplyInlineSummary("))
        assertTrue(source.contains("val validationAndPathsLine = buildString {"))
        assertTrue(source.contains("appendLine(summaryLine)"))
        assertTrue(source.contains("appendLine(result.summaryLine())"))
        assertTrue(source.contains("append(changedFilesText)"))
        assertTrue(source.contains("summaryLine = summaryLine"))
        assertTrue(source.contains("validationAndPathsLine = validationAndPathsLine,"))
        assertTrue(source.contains("nextStepLine = nextActionLine"))
        assertTrue(source.contains("appendLine(\"- Next: Refresh UML From Code to verify.\")"))
    }

    @Test
    fun `review summary switches to inline applied summary when apply succeeds`() {
        assertTrue(source.contains("reviewSummaryArea.text = if (n.executionStatus == ExecutionStatus.APPLIED && inlineSummary != null) {"))
        assertTrue(source.contains("postApplyReviewSummary(exec, inlineSummary)"))
        assertTrue(source.contains("appendLine(summary.validationAndPathsLine)"))
        assertTrue(source.contains("appendLine(summary.verifyChecklist)"))
        assertTrue(source.contains("append(\"\\nVerified receipt:\\n- Review the changed paths, validation result, and inferred run command above.\\n- Blueprint already refreshed the code-backed UML automatically after apply.\\n- Refresh UML From Code to run a separate manual verification refresh.\\n- Run the changed app to confirm the feature exists.\\n- Open Changed Files is optional after verification if you want to inspect what Blueprint wrote.\")"))
    }
}
