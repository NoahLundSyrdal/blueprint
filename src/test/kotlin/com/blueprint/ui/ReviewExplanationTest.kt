package com.blueprint.ui

import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.Patch
import com.blueprint.model.ReviewArtifact
import com.blueprint.model.ReviewIssue
import com.blueprint.model.ScopeCompliance
import com.blueprint.service.DependencyGraphService
import com.blueprint.service.ProjectValidationService
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewExplanationTest {
    @Test
    fun `approval summary explains why apply is safe`() {
        val text = ReviewExplanation.summary(
            nodeTitle = "Invite update",
            exec = ExecutionArtifact(
                patches = listOf(
                    Patch(
                        path = "app/models.py",
                        action = "update",
                        content = "class Invite:\n    accepted_at: datetime",
                    ),
                ),
                summary = "Add accepted_at to Invite.",
            ),
            review = ReviewArtifact(
                reviewStatus = "APPROVE",
                summary = "Patch is scoped and safe.",
                scopeCompliance = ScopeCompliance(result = "PASS"),
                positiveSignals = listOf("Only the selected model file changes.", "Fields match the UML request."),
                recommendedNextAction = "apply",
            ),
            readiness = DependencyGraphService.NodeReadiness(nodeId = "invite-update", ready = true, reasons = emptyList(), wave = 1),
            validation = null,
        )

        assertTrue(text.contains("Approved because Invite + accepted_at: datetime stays aligned with Invite update"))
        assertTrue(text.contains("Scope: stays within the selected files."))
        assertTrue(text.contains("Safety: Only the selected model file changes. Fields match the UML request."))
        assertTrue(text.contains("Dependency status: ready."))
        assertTrue(text.contains("Validation status: will run after apply"))
    }

    @Test
    fun `rejection summary explains blocker and fix`() {
        val text = ReviewExplanation.summary(
            nodeTitle = "Invite update",
            exec = ExecutionArtifact(
                patches = listOf(Patch(path = "app/models.py", action = "update", content = "class Invite:\n    accepted_at: datetime")),
                summary = "Add accepted_at to Invite.",
            ),
            review = ReviewArtifact(
                reviewStatus = "REQUEST_CHANGES",
                summary = "Patch touches the wrong file.",
                scopeCompliance = ScopeCompliance(result = "FAIL"),
                issues = listOf(
                    ReviewIssue(
                        title = "Out of scope file",
                        details = "The patch also modifies app/routes.py.",
                        suggestedFix = "Regenerate the patch so only app/models.py changes.",
                    ),
                ),
                recommendedNextAction = "revise",
            ),
            readiness = DependencyGraphService.NodeReadiness(nodeId = "invite-update", ready = false, reasons = listOf("parent node not applied"), wave = 2),
            validation = ProjectValidationService.ValidationResult(
                status = ProjectValidationService.ValidationResult.Status.SKIPPED,
                reason = "No Python validation command was inferred for this project.",
            ),
        )

        assertTrue(text.contains("Not approved because The patch also modifies app/routes.py."))
        assertTrue(text.contains("Fix: Regenerate the patch so only app/models.py changes."))
        assertTrue(text.contains("Scope: review found out-of-scope changes."))
        assertTrue(text.contains("Dependency status: blocked by parent node not applied."))
        assertTrue(text.contains("Validation status: skipped after apply."))
    }
}
