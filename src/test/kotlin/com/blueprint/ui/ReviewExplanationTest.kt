package com.blueprint.ui

import com.blueprint.model.AcceptanceReviewItem
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
    fun `approval status line summarizes why apply is safe`() {
        val text = ReviewExplanation.statusLine(
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
                positiveSignals = listOf("Only the selected model file changes."),
                recommendedNextAction = "apply",
            ),
        )

        assertTrue(text.contains("Review approved Invite update because Invite + accepted_at: datetime stays in scope."))
        assertTrue(text.contains("Only the selected model file changes."))
    }

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
                acceptanceReview = listOf(
                    AcceptanceReviewItem(
                        criterion = "Add accepted_at field",
                        result = "PASS",
                        evidence = listOf("The Invite patch adds accepted_at as requested by the UML."),
                    ),
                ),
                positiveSignals = listOf("Only the selected model file changes.", "Fields match the UML request."),
                recommendedNextAction = "apply",
            ),
            readiness = DependencyGraphService.NodeReadiness(nodeId = "invite-update", ready = true, reasons = emptyList(), wave = 1),
            validation = null,
        )

        assertTrue(text.contains("Why is it safe to apply?"))
        assertTrue(text.contains("- Approved because Invite + accepted_at: datetime stays aligned with Invite update"))
        assertTrue(text.contains("- Acceptance: The Invite patch adds accepted_at as requested by the UML."))
        assertTrue(text.contains("- Scope: stays within the selected files."))
        assertTrue(text.contains("- Safety: Only the selected model file changes. Fields match the UML request."))
        assertTrue(text.contains("- Dependency status: ready."))
        assertTrue(text.contains("- Validation status: will run after apply"))
    }


    @Test
    fun `approval summary covers skipped validation and fallback safety text`() {
        val text = ReviewExplanation.summary(
            nodeTitle = "Invite update",
            exec = ExecutionArtifact(
                patches = listOf(
                    Patch(
                        path = "app/models.py",
                        action = "update",
                        content = """
                            class Invite:
                                accepted_at: datetime
                        """.trimIndent(),
                    ),
                ),
                summary = "Add accepted_at to Invite.",
            ),
            review = ReviewArtifact(
                reviewStatus = "APPROVE",
                summary = "Patch is scoped and safe.",
                scopeCompliance = ScopeCompliance(result = "PASS"),
                positiveSignals = emptyList(),
                recommendedNextAction = "apply",
            ),
            readiness = DependencyGraphService.NodeReadiness(nodeId = "invite-update", ready = true, reasons = emptyList(), wave = 1),
            validation = ProjectValidationService.ValidationResult(
                status = ProjectValidationService.ValidationResult.Status.SKIPPED,
                reason = "No command inferred.",
            ),
        )

        assertTrue(text.contains("- Safety: No concrete safety issues were reported."))
        assertTrue(text.contains("- Validation status: skipped after apply."))
    }

    @Test
    fun `rejection status line summarizes blocker and fix`() {
        val text = ReviewExplanation.statusLine(
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
        )

        assertTrue(text.contains("Review blocked Invite update because The patch also modifies app/routes.py."))
        assertTrue(text.contains("Fix: Regenerate the patch so only app/models.py changes."))
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
                acceptanceReview = listOf(
                    AcceptanceReviewItem(
                        criterion = "Add accepted_at field",
                        result = "FAIL",
                        issues = listOf("The patch edits app/routes.py instead of only the requested model file."),
                    ),
                ),
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

        assertTrue(text.contains("Why is it blocked?"))
        assertTrue(text.contains("- Not approved because The patch also modifies app/routes.py."))
        assertTrue(text.contains("- Fix: Regenerate the patch so only app/models.py changes."))
        assertTrue(text.contains("- Acceptance: The patch edits app/routes.py instead of only the requested model file."))
        assertTrue(text.contains("- Scope: review found out-of-scope changes."))
        assertTrue(text.contains("- Dependency status: blocked by parent node not applied."))
        assertTrue(text.contains("- Validation status: skipped after apply."))
    }
}
