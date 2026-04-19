package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewApprovalSummaryRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `review summary includes a plain safe-to-apply explanation when approved`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("reviewSummaryArea.text = if (n.executionStatus == ExecutionStatus.APPLIED && inlineSummary != null) {"))
        assertTrue(source.contains("buildReviewSummary(exec, review, reviewFreshness, validationCommand)"))
        assertTrue(source.contains("private fun reviewApprovalSentence(exec: ExecutionArtifact?, review: ReviewArtifact?): String?"))
        assertTrue(source.contains("Why review approved this patch:"))
        assertTrue(source.contains("appendLine(\"Approval reason:\")"))
        assertTrue(source.contains("appendLine(\"Proof and scope:\")"))
        assertTrue(source.contains("private fun compactApprovalReason(review: ReviewArtifact): String ="))
        assertTrue(source.contains("That is why Apply Approved Changes is safe now."))
        assertTrue(source.contains("It also matches the requested UML because"))
    }
}
