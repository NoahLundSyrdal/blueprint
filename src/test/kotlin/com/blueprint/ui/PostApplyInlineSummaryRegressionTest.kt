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
        assertTrue(source.contains("appendLine(\"- Next: Refresh UML From Code to verify the updated code-backed UML.\")"))
    }

    @Test
    fun `review summary switches to inline applied summary when apply succeeds`() {
        assertTrue(source.contains("reviewSummaryArea.text = if (n.executionStatus == ExecutionStatus.APPLIED && inlineSummary != null) {"))
        assertTrue(source.contains("postApplyReviewSummary(exec, inlineSummary)"))
        assertTrue(source.contains("val reviewText = summary.reviewPanelText(exec)"))
        assertTrue(source.contains("if (reviewText.contains(summary.validationDetailsText)) return reviewText"))
        assertTrue(source.contains("fun reviewPanelText(exec: ExecutionArtifact?): String ="))
        assertTrue(source.contains("appendLine(receiptSummary)"))
        assertTrue(source.contains("appendLine(resultDetailsSection())"))
        assertTrue(source.contains("private fun resultDetailsSection(): String ="))
        assertTrue(source.contains("appendLine(verifyChecklist)"))
        assertTrue(source.contains("append(\"\\nVerified receipt:\\n- Review the changed paths, validation result, and inferred run command above.\\n- Blueprint already refreshed the code-backed UML automatically after apply.\\n- Use Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself.\\n- Run the changed app to confirm the feature exists.\\n- Open Changed Files is optional after verification if you want to inspect what Blueprint wrote.\")"))
        assertTrue(source.contains("Verify in code: Use Open Changed File to inspect the primary changed file in the IDE. This does not apply or refresh anything."))
    }
}
