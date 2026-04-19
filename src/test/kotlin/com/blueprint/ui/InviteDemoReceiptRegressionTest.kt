package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteDemoReceiptRegressionTest {
    @Test
    fun `invite demo receipt and checklist preserve standard flow language`() {
        val checklist = GuidedInviteScenario.checklistText(
            GuidedInviteScenarioState(
                codeMapReady = true,
                prompt = "add an InviteReminder entity",
                expectedEntity = "InviteReminder",
                expectedRelationSource = "Invite",
                expectedRelationTarget = "InviteReminder",
                resetSuggested = false,
                resetPath = GuidedInviteScenario.PATCH_PATH,
                umlDraftReady = true,
                reviewedDiffReady = true,
                reviewApprovedReady = true,
                appliedReady = true,
                refreshedCodeMapReady = true,
                runVerified = false,
                promptReady = true,
                runCommand = "python main.py",
            ),
        )
        val receipt = GuidedInviteScenarioState(
            codeMapReady = true,
            prompt = "add an InviteReminder entity",
            expectedEntity = "InviteReminder",
            expectedRelationSource = "Invite",
            expectedRelationTarget = "InviteReminder",
            resetSuggested = false,
            resetPath = GuidedInviteScenario.PATCH_PATH,
            umlDraftReady = true,
            reviewedDiffReady = true,
            reviewApprovedReady = true,
            appliedReady = true,
            refreshedCodeMapReady = true,
            runVerified = true,
            promptReady = true,
            runCommand = "python main.py",
        ).demoReceiptText()

        assertTrue(checklist.contains("Generate Code Diff -> expect a reviewed code patch for blueprint_demo/imported_invite/models.py."))
        assertTrue(checklist.contains("Apply Approved Changes -> expect the imported invite patch to be written to disk after review approval."))
        assertTrue(checklist.contains("Refresh UML From Code -> expect InviteReminder to appear in the refreshed current code map."))

        assertTrue(receipt.contains("Demo receipt:"))
        assertTrue(receipt.contains("[pass] Refresh UML From Code -> expected visible result: current code map is loaded."))
        assertTrue(receipt.contains("[pass] Generate Code Diff -> expected visible result: reviewed code patch is ready."))
        assertTrue(receipt.contains("[pass] Review approved -> expected visible result: Apply Approved Changes is unlocked."))
        assertTrue(receipt.contains("[pass] Apply Approved Changes -> expected visible result: code files are written to disk."))
        assertTrue(receipt.contains("[pass] Refresh UML From Code again -> expected visible result: code-backed UML reflects the applied change."))
        assertTrue(receipt.contains("[pass] Run the changed app -> verified with: python main.py"))

        assertFalse(checklist.contains("Create Code Nodes"))
        assertFalse(receipt.contains("Create Code Nodes"))
        assertFalse(checklist.contains("create reviewable nodes"))
        assertFalse(receipt.contains("create nodes"))
    }
}
