package com.blueprint.ui

import com.blueprint.model.AcceptanceReviewItem
import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.Patch
import com.blueprint.model.PatchAction
import com.blueprint.model.ReviewArtifact
import com.blueprint.model.ScopeCompliance
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyApprovalSummaryScopeRegressionTest {
    @Test
    fun `semantic summary only reflects files in reviewed patch`() {
        val exec = ExecutionArtifact(
            summary = "Added InvitePolicy model in app/models.py. Added InvitePolicyForm helper in app/forms.py.",
            patches = listOf(
                Patch(path = "app/models.py", action = PatchAction.update.name, content = "class InvitePolicy"),
            ),
        )
        val review = ReviewArtifact(
            reviewStatus = "APPROVE",
            summary = "Looks good",
            scopeCompliance = ScopeCompliance(result = "PASS"),
            acceptanceReview = listOf(
                AcceptanceReviewItem(
                    criterion = "Invite policy is represented",
                    result = "PASS",
                    evidence = listOf("the UML now includes InvitePolicy"),
                )
            ),
        )

        val sentence = reviewApprovalSentenceForTest(exec, review)

        assertEquals(
            "Why review approved this patch: Updated class InvitePolicy stays within the selected files, and no blocking safety issues were reported. It also matches the requested UML because the UML now includes InvitePolicy.",
            sentence,
        )
    }

    private fun reviewApprovalSentenceForTest(exec: ExecutionArtifact, review: ReviewArtifact): String {
        val changePhrase = PatchChangeSummary.semanticChangeLines(exec, exec.patches.map { it.path })
            .take(2)
            .ifEmpty { listOf("the reviewed code patch") }
            .joinToString(" and ")
        val acceptedEvidence = review.acceptanceReview.orEmpty()
            .filter { it.result.uppercase() == "PASS" }
            .flatMap { it.evidence }
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(1)
            .firstOrNull()
        val scopeReason = when (review.scopeCompliance.result.uppercase()) {
            "PASS" -> "stays within the selected files"
            "PARTIAL" -> "mostly stays within the selected files"
            else -> "was reviewed for scope"
        }
        val evidenceReason = acceptedEvidence?.let { " It also matches the requested UML because $it." }.orEmpty()
        return "Why review approved this patch: $changePhrase $scopeReason, and no blocking safety issues were reported.$evidenceReason"
    }
}
