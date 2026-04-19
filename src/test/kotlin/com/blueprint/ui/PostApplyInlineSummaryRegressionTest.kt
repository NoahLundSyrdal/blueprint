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
        assertTrue(source.contains("val receiptSummary = buildString {"))
        assertTrue(source.contains("appendLine(\"Apply receipt:\")"))
        assertTrue(source.contains("appendLine(\"- ") && source.contains("summaryLine\")"))
        assertTrue(source.contains("appendLine(\"- ") && source.contains("writtenPathCountLine\")"))
        assertTrue(source.contains("appendLine(\"- ") && source.contains("validationOutcomeLine\")"))
        assertTrue(source.contains("appendLine(\"- ") && source.contains("nextActionLine\")"))
        assertTrue(source.contains("val validationAndPathsLine = buildString {"))
        assertTrue(source.contains("appendLine(receiptSummary)"))
        assertTrue(source.contains("val validationDetailsText = validationReportText(result)"))
        assertTrue(source.contains("appendLine(validationDetailsText)"))
        assertTrue(source.contains("append(writtenPathsText)"))
        assertTrue(source.contains("summaryLine = summaryLine"))
        assertTrue(source.contains("receiptSummary = receiptSummary,"))
        assertTrue(source.contains("validationDetailsText = validationDetailsText,"))
        assertTrue(source.contains("validationAndPathsLine = validationAndPathsLine,"))
        assertTrue(source.contains("nextStepLine = nextActionLine"))
        assertTrue(source.contains("appendLine(\"- \$POST_APPLY_NEXT_STEP_LINE\")"))
    }

    @Test
    fun `review summary switches to inline applied summary when apply succeeds`() {
        assertTrue(source.contains("reviewSummaryArea.text = if (n.executionStatus == ExecutionStatus.APPLIED && inlineSummary != null) {"))
        assertTrue(source.contains("postApplyReviewSummary(exec, inlineSummary)"))
        assertTrue(source.contains("private fun postApplyReviewSummary(exec: ExecutionArtifact?, summary: PostApplyInlineSummary): String ="))
        assertTrue(source.contains("summary.reviewPanelText(exec)"))
        assertTrue(source.contains("fun reviewPanelText(exec: ExecutionArtifact?): String ="))
        assertTrue(source.contains("appendLine(receiptSummary)"))
        assertTrue(source.contains("appendLine(resultDetailsSection())"))
        assertTrue(source.contains("private fun resultDetailsSection(): String ="))
        assertTrue(source.contains("appendLine(verifyChecklist)"))
        assertTrue(source.contains("append(POST_APPLY_VERIFIED_RECEIPT)"))
        assertTrue(source.contains("Verify in code: Use Open Changed File to inspect the primary changed file in the IDE. This does not apply or refresh anything."))
    }
}
