package com.blueprint.ui

import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.Patch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostApplyInlineSummaryBehaviorTest {
    @Test
    fun `review panel text keeps concise apply result separate from next verification step`() {
        val summary = PostApplyInlineSummary(
            changedPaths = listOf("app/models.py"),
            summaryLine = "Applied 1 file. Validation passed.",
            receiptSummary = "Apply receipt:\n- Applied 1 file. Validation passed.\n- Written paths (1): app/models.py\n- Validation outcome: passed.\n- Next: Refresh UML From Code to verify the updated code-backed UML.",
            validationAndPathsLine = "Apply receipt:\n- Applied 1 file. Validation passed.\n- Written paths (1): app/models.py\n- Validation outcome: passed.\n- Next: Refresh UML From Code to verify the updated code-backed UML.\nValidation passed after apply.\nRun after apply:\npython app.py\nChanged paths:\n- app/models.py",
            nextStepLine = "Next: Refresh UML From Code to verify the updated code-backed UML.",
            verifyChecklist = "Refresh UML From Code verification:\n- Blueprint already reloaded the changed code into the UML automatically after apply.\n- Click Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself.",
            copyableResultSummary = "Result summary\n- Applied 1 file. Validation passed.",
        )
        val exec = ExecutionArtifact(
            status = "SUCCESS",
            summary = "Added InviteReminder",
            touchedFiles = emptyList(),
            rawJson = "",
            patches = listOf(
                Patch(
                    path = "app/models.py",
                    action = "update",
                    content = "@@\n class InviteReminder\n+  send_at: datetime\n",
                ),
            ),
        )

        val reviewPanelText = summary.reviewPanelText(exec)

        assertTrue(reviewPanelText.lines().first() == "Applied 1 file. Validation passed.")
        assertTrue(reviewPanelText.contains("What changed?"))
        assertTrue(reviewPanelText.contains("Plain-English summary after apply:"))
        assertTrue(reviewPanelText.contains("Refresh UML From Code verification:"))
        assertFalse(reviewPanelText.lines().first().contains("Next: Refresh UML From Code"))
    }

    @Test
    fun `review panel text uses changed paths to scope apply summary output`() {
        val summary = PostApplyInlineSummary(
            changedPaths = listOf("app/models.py"),
            summaryLine = "Applied 1 file. Validation passed.",
            receiptSummary = "Apply receipt:\n- Applied 1 file. Validation passed.",
            validationAndPathsLine = "Applied 1 file. Validation passed.",
            nextStepLine = "Next: Refresh UML From Code to verify the updated code-backed UML.",
            verifyChecklist = "Refresh UML From Code verification:",
            copyableResultSummary = "Result summary",
        )
        val exec = ExecutionArtifact(
            status = "SUCCESS",
            summary = "Updated models",
            touchedFiles = emptyList(),
            rawJson = "",
            patches = listOf(
                Patch(path = "app/models.py", action = "update", content = "@@\n class Invite\n+  expires_at: datetime\n"),
                Patch(path = "app/other.py", action = "update", content = "@@\n class Noise\n+  ignored: str\n"),
            ),
        )

        val reviewPanelText = summary.reviewPanelText(exec)

        assertTrue(reviewPanelText.contains("Invite + expires_at: datetime"))
        assertFalse(reviewPanelText.contains("Noise + ignored: str"))
    }

    @Test
    fun `no-op apply summary stays honest when no files were written`() {
        val summary = PostApplyInlineSummary(
            changedPaths = emptyList(),
            summaryLine = "No code changes needed. Validation passed.",
            receiptSummary = "Apply receipt:\n- No code changes needed. Validation passed.\n- Written paths: none.\n- Validation outcome: passed.\n- Next: Refresh UML From Code to verify the updated code-backed UML.",
            validationAndPathsLine = "Apply receipt:\n- No code changes needed. Validation passed.",
            nextStepLine = "Next: Refresh UML From Code to verify the updated code-backed UML.",
            verifyChecklist = "Refresh UML From Code verification:\n- No changed paths were written.",
            copyableResultSummary = "Result summary\n- No code changes needed. Validation passed.",
        )
        val exec = ExecutionArtifact(
            status = "SUCCESS",
            summary = "Nothing to apply.",
            touchedFiles = emptyList(),
            rawJson = "",
            patches = listOf(
                Patch(path = "app/models.py", action = "update", content = "class Invite:\n    accepted_at: datetime"),
            ),
        )

        val reviewPanelText = summary.reviewPanelText(exec)

        assertTrue(reviewPanelText.lines().first() == "No code changes needed. Validation passed.")
        assertTrue(reviewPanelText.contains("Changed files: none."))
        assertFalse(reviewPanelText.contains("Applied 0 files."))
    }
}
