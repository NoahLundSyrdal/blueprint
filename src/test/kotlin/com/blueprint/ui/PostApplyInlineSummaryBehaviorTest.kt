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
            validationAndPathsLine = "Applied 1 file. Validation passed.\nValidation passed after apply.\nRun after apply:\npython app.py\nChanged paths:\n- app/models.py",
            nextStepLine = "Next: Refresh UML From Code to manually verify the updated code-backed UML.",
            verifyChecklist = "Refresh UML From Code verification:\n- Blueprint already reloaded the changed code into the UML automatically after apply.\n- Click Refresh UML From Code when you want a separate manual verification refresh.",
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
            validationAndPathsLine = "Applied 1 file. Validation passed.",
            nextStepLine = "Next: Refresh UML From Code to manually verify the updated code-backed UML.",
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
}
