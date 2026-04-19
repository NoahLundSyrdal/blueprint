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
            validationDetailsText = "Validate project command: python -m pytest\nValidation passed after apply.",
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
        assertTrue(reviewPanelText.split("Apply receipt:").size == 2)
    }

    @Test
    fun `review panel text uses changed paths to scope apply summary output`() {
        val summary = PostApplyInlineSummary(
            changedPaths = listOf("app/models.py"),
            summaryLine = "Applied 1 file. Validation passed.",
            receiptSummary = "Apply receipt:\n- Applied 1 file. Validation passed.",
            validationDetailsText = "Validate project command: python -m pytest\nValidation passed after apply.",
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
            validationDetailsText = "Validate project command: not available\nValidation passed after apply.",
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

    @Test
    fun `review panel text includes verification receipt guidance without duplicating apply receipt heading`() {
        val summary = PostApplyInlineSummary(
            changedPaths = listOf("app/models.py"),
            summaryLine = "Applied 1 file. Validation passed.",
            receiptSummary = "Apply receipt:\n- Applied 1 file. Validation passed.\n- Written paths (1): app/models.py\n- Validation outcome: passed.\n- Next: Refresh UML From Code to verify the updated code-backed UML.",
            validationDetailsText = "Validate project command: python -m pytest\nValidation passed after apply.",
            validationAndPathsLine = "Apply receipt:\n- Applied 1 file. Validation passed.\n- Written paths (1): app/models.py\n- Validation outcome: passed.\n- Next: Refresh UML From Code to verify the updated code-backed UML.\nValidation passed after apply.\nRun after apply:\npython app.py\nChanged paths:\n- app/models.py",
            nextStepLine = "Next: Refresh UML From Code to verify the updated code-backed UML.",
            verifyChecklist = "Refresh UML From Code verification:\n- Blueprint already reloaded the changed code into the UML automatically after apply.\n- Use Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself.\n- Applied 1 file. Validation passed.\n- Validation passed after apply.\n- Written paths:\n  - app/models.py\n- Blueprint automatically refreshed the code-backed UML from disk after apply.\n- Blueprint already refreshed the code-backed UML automatically after apply. Use Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself.",
            copyableResultSummary = "Result summary\n- Applied 1 file. Validation passed.",
        )

        val reviewPanelText = summary.reviewPanelText(exec = null)

        assertTrue(reviewPanelText.contains("Verified receipt:"))
        assertTrue(reviewPanelText.contains("- Review the changed paths, validation result, and inferred run command above."))
        assertTrue(reviewPanelText.contains("- Run the changed app to confirm the feature exists."))
        assertTrue(reviewPanelText.contains("- Open Changed Files is optional after verification if you want to inspect what Blueprint wrote."))
        assertTrue(reviewPanelText.split("Apply receipt:").size == 2)
    }

    @Test
    fun `review panel text preserves verification checklist context for changed paths and refresh guidance`() {
        val summary = PostApplyInlineSummary(
            changedPaths = listOf("app/models.py", "app/routes.py"),
            summaryLine = "Applied 2 files. Validation skipped because no command was inferred.",
            receiptSummary = "Apply receipt:\n- Applied 2 files. Validation skipped because no command was inferred.\n- Written paths (2): app/models.py, app/routes.py\n- Validation outcome: skipped because no command was inferred.\n- Next: Refresh UML From Code to verify the updated code-backed UML.",
            validationDetailsText = "Validate project command: not available\nValidation skipped after apply.",
            validationAndPathsLine = "Apply receipt:\n- Applied 2 files. Validation skipped because no command was inferred.\n- Written paths (2): app/models.py, app/routes.py\n- Validation outcome: skipped because no command was inferred.\n- Next: Refresh UML From Code to verify the updated code-backed UML.\nValidation skipped after apply.\nRun after apply:\npython app.py\nWritten paths:\n- app/models.py\n- app/routes.py",
            nextStepLine = "Next: Refresh UML From Code to verify the updated code-backed UML.",
            verifyChecklist = "Refresh UML From Code verification:\n- Blueprint already reloaded the changed code into the UML automatically after apply.\n- Use Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself.\n- Applied 2 files. Validation skipped because no command was inferred.\n- Validation skipped after apply.\n- Written paths:\n  - app/models.py\n  - app/routes.py\n- Blueprint refreshed the code-backed UML after apply, but did not find a matching UML entity to highlight from the changed paths.\n- Blueprint already refreshed the code-backed UML automatically after apply. Use Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself.",
            copyableResultSummary = "Result summary\n- Applied 2 files. Validation skipped because no command was inferred.",
        )

        val reviewPanelText = summary.reviewPanelText(exec = null)

        assertTrue(reviewPanelText.contains("Refresh UML From Code verification:"))
        assertTrue(reviewPanelText.contains("- Written paths:"))
        assertTrue(reviewPanelText.contains("  - app/models.py"))
        assertTrue(reviewPanelText.contains("  - app/routes.py"))
        assertTrue(reviewPanelText.contains("did not find a matching UML entity to highlight"))
        assertTrue(reviewPanelText.contains("Use Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself."))
    }
}
