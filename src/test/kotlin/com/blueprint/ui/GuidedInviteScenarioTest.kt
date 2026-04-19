package com.blueprint.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedInviteScenarioTest {
    @Test
    fun `matches bundled invite project by name or path`() {
        assertTrue(GuidedInviteScenario.matchesProject("invite_project", null))
        assertTrue(GuidedInviteScenario.matchesProject("anything", "/tmp/work/examples/invite_project"))
        assertTrue(!GuidedInviteScenario.matchesProject("car_company_project", "/tmp/work/examples/car_company_project"))
    }

    @Test
    fun `picks a fresh invite demo prompt from current state`() {
        assertEquals(
            "add an InvitePolicy entity",
            GuidedInviteScenario.pickPrompt(
                componentNames = setOf("Project", "User", "Invite"),
                entityNames = emptySet(),
                inviteFields = emptyList(),
            )?.prompt,
        )
        assertEquals(
            "add an InviteReminder entity",
            GuidedInviteScenario.pickPrompt(
                componentNames = setOf("Project", "User", "Invite", "InvitePolicy"),
                entityNames = setOf("InvitePolicy"),
                inviteFields = emptyList(),
            )?.prompt,
        )
        assertEquals(
            "add an expires_at field to Invite",
            GuidedInviteScenario.pickPrompt(
                componentNames = setOf("Project", "User", "Invite", "InvitePolicy", "InviteReminder"),
                entityNames = setOf("InvitePolicy", "InviteReminder"),
                inviteFields = listOf("id", "email"),
            )?.prompt,
        )
        assertEquals(
            null,
            GuidedInviteScenario.pickPrompt(
                componentNames = setOf("Project", "User", "Invite", "InvitePolicy", "InviteReminder"),
                entityNames = setOf("InvitePolicy", "InviteReminder"),
                inviteFields = listOf("id", "email", "expires_at"),
            ),
        )
    }

    @Test
    fun `checklist advances through selected guided prompt steps`() {
        val first = GuidedInviteScenario.checklistText(
            GuidedInviteScenarioState(
                codeMapReady = false,
                prompt = "add an InvitePolicy entity",
                expectedEntity = "InvitePolicy",
                expectedRelationSource = "Invite",
                expectedRelationTarget = "InvitePolicy",
                resetSuggested = false,
                resetPath = GuidedInviteScenario.PATCH_PATH,
                umlDraftReady = false,
                reviewedDiffReady = false,
                reviewApprovedReady = false,
                appliedReady = false,
                refreshedCodeMapReady = false,
                runVerified = false,
                promptReady = false,
                runCommand = null,
            ),
        )
        val firstLines = first.lines()
        assertEquals("Demo prompt scenario:", firstLines[0])
        assertTrue(firstLines[1].startsWith("[next]"))
        assertTrue(first.contains("load the current code map first so Blueprint can choose a fresh demo change"))
        assertTrue(first.contains("Try This Change -> available after the current code map loads."))
        assertTrue(first.contains("Blueprint reviews the code patch before apply."))
        assertTrue(first.contains("Apply Approved Changes -> blocked until review approves the reviewed code patch."))
        assertTrue(first.contains("Run the changed app -> no run command was inferred yet, so open the project entrypoint manually to verify the feature."))
        assertTrue(first.contains("Your own change:"))

        val middle = GuidedInviteScenario.checklistText(
            GuidedInviteScenarioState(
                codeMapReady = true,
                prompt = "add an InviteReminder entity",
                expectedEntity = "InviteReminder",
                expectedRelationSource = "Invite",
                expectedRelationTarget = "InviteReminder",
                resetSuggested = false,
                resetPath = GuidedInviteScenario.PATCH_PATH,
                umlDraftReady = false,
                reviewedDiffReady = false,
                reviewApprovedReady = false,
                appliedReady = false,
                refreshedCodeMapReady = false,
                runVerified = false,
                promptReady = true,
                runCommand = null,
            ),
        )
        val runReady = GuidedInviteScenario.checklistText(
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
                runVerified = true,
                promptReady = true,
                runCommand = "python main.py",
            ),
        )
        val approved = GuidedInviteScenario.checklistText(
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
                appliedReady = false,
                refreshedCodeMapReady = false,
                runVerified = false,
                promptReady = true,
                runCommand = null,
            ),
        )
        val middleLines = middle.lines()
        assertEquals("Demo prompt scenario:", middleLines[0])
        assertTrue(middleLines[1].startsWith("[done]"))
        assertTrue(middleLines[2].startsWith("[next]"))
        assertTrue(middle.contains("Try This Change: \"add an InviteReminder entity\""))
        assertTrue(middle.contains("InviteReminder linked from Invite"))
        assertTrue(middle.contains("Review approved -> the reviewed code patch is approved and Apply Approved Changes is now unlocked."))
        assertTrue(middle.indexOf("Generate Code Diff -> expect a reviewed code patch") < middle.indexOf("Review approved -> the reviewed code patch is approved and Apply Approved Changes is now unlocked."))
        assertTrue(middle.indexOf("Review approved -> the reviewed code patch is approved and Apply Approved Changes is now unlocked.") < middle.indexOf("Apply Approved Changes -> expect the imported invite patch to be written to disk after review approval."))
        assertTrue(approved.contains("[done] Review approved -> the reviewed code patch is approved and Apply Approved Changes is now unlocked."))
        assertTrue(approved.contains("[next] Apply Approved Changes -> expect the imported invite patch to be written to disk after review approval."))
        assertTrue(runReady.contains("[done] Refresh UML From Code -> expect InviteReminder to appear in the refreshed current code map."))
        assertTrue(runReady.contains("[done] Run the changed app -> pass. Verified with: python main.py"))

        val loadedPrompt = GuidedInviteScenario.checklistText(
            GuidedInviteScenarioState(
                codeMapReady = true,
                prompt = "add an InvitePolicy entity",
                expectedEntity = "InvitePolicy",
                expectedRelationSource = "Invite",
                expectedRelationTarget = "InvitePolicy",
                resetSuggested = false,
                resetPath = GuidedInviteScenario.PATCH_PATH,
                umlDraftReady = false,
                reviewedDiffReady = false,
                reviewApprovedReady = false,
                appliedReady = false,
                refreshedCodeMapReady = false,
                runVerified = false,
                promptReady = false,
                runCommand = null,
            ),
        )
        assertTrue(loadedPrompt.contains("Try This Change -> use the button to load the fresh prompt into chat first."))

        val fieldPrompt = GuidedInviteScenario.checklistText(
            GuidedInviteScenarioState(
                codeMapReady = true,
                prompt = "add an expires_at field to Invite",
                expectedEntity = "Invite",
                expectedRelationSource = null,
                expectedRelationTarget = null,
                resetSuggested = false,
                resetPath = GuidedInviteScenario.PATCH_PATH,
                umlDraftReady = false,
                reviewedDiffReady = false,
                reviewApprovedReady = false,
                appliedReady = false,
                refreshedCodeMapReady = false,
                runVerified = false,
                promptReady = true,
                runCommand = null,
            ),
        )
        assertTrue(fieldPrompt.contains("expect Invite to include expires_at in the UML draft."))
        assertTrue(fieldPrompt.contains("expect the refreshed current code map to include expires_at on Invite."))

        val reset = GuidedInviteScenario.checklistText(
            GuidedInviteScenarioState(
                codeMapReady = true,
                prompt = "restore blueprint_demo/imported_invite/models.py from git",
                expectedEntity = "Invite",
                expectedRelationSource = null,
                expectedRelationTarget = null,
                resetSuggested = true,
                resetPath = GuidedInviteScenario.PATCH_PATH,
                umlDraftReady = false,
                reviewedDiffReady = false,
                reviewApprovedReady = false,
                appliedReady = false,
                refreshedCodeMapReady = false,
                runVerified = false,
                promptReady = true,
                runCommand = null,
            ),
        )
        assertTrue(reset.contains("Guided demo prompt loaded: \"restore blueprint_demo/imported_invite/models.py from git\"."))
        assertTrue(reset.contains("restoring blueprint_demo/imported_invite/models.py"))
        assertTrue(reset.contains("Generate Code Diff -> wait until the sandbox is reset or you choose your own new UML change."))
        assertTrue(reset.contains("Blueprint reviews the fresh code patch before apply."))
        assertTrue(reset.contains("Apply Approved Changes -> blocked until review approves the fresh reviewed code patch."))
        assertTrue(reset.indexOf("Generate Code Diff -> wait until the sandbox is reset or you choose your own new UML change.") < reset.indexOf("Blueprint reviews the fresh code patch before apply."))
        assertTrue(reset.indexOf("Blueprint reviews the fresh code patch before apply.") < reset.indexOf("Apply Approved Changes -> blocked until review approves the fresh reviewed code patch."))
        assertTrue(reset.contains("Your own change:"))
    }


    @Test
    fun `reset state switches button copy to reset guidance`() {
        val source = java.nio.file.Files.readString(java.nio.file.Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("firstRunPromptButton.text = if (state.resetSuggested) \"Show Reset Steps\" else \"Try This Change\""))
        assertTrue(source.contains("runDemoButton.text = if (state.runVerified) \"Demo Run Verified\" else \"Run Demo Step\""))
        assertTrue(source.contains("All guided demo changes already exist. Restore \${state.resetPath} from git, or pick your own change."))
        assertTrue(source.contains("To get a fresh invite demo path, restore \${state.resetPath} from git or rerun the example sandbox setup, then click Refresh UML From Code."))
    }

    @Test
    fun `fresh guided prompt copy stays discoverable in ui source`() {
        val source = java.nio.file.Files.readString(java.nio.file.Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("use Try This Change for a fresh prompt based on the current sandbox state"))
        assertTrue(source.contains("Blueprint keeps this Try This Change prompt fresh by checking the current UML and imported invite code before suggesting the next demo change."))
        assertTrue(source.contains("load the current code map first so Blueprint can choose a fresh demo change"))
        assertTrue(source.contains("Fresh demo prompt: "))
        assertTrue(source.contains("Fresh demo prompt loaded into chat:"))
        assertTrue(source.contains("Demo e2e step passed: Try This Change prepared"))
        assertTrue(source.contains("Manual demo runner"))
        assertTrue(source.contains("Run Demo Step"))
        assertTrue(source.contains("Try This Change -> use the button to load the fresh prompt into chat first."))
    }
}
