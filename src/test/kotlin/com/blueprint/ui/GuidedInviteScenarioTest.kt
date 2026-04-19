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
                appliedReady = false,
                refreshedCodeMapReady = false,
                promptReady = false,
            ),
        )
        val firstLines = first.lines()
        assertEquals("Demo prompt scenario:", firstLines[0])
        assertTrue(firstLines[1].startsWith("[next]"))
        assertTrue(first.contains("load the current code map first so Blueprint can choose a fresh demo change"))
        assertTrue(first.contains("Try This Change -> available after the current code map loads."))
        assertTrue(first.contains("Review Approved Changes -> wait for Blueprint to approve the reviewed code patch before apply."))
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
                appliedReady = false,
                refreshedCodeMapReady = false,
                promptReady = true,
            ),
        )
        val middleLines = middle.lines()
        assertEquals("Demo prompt scenario:", middleLines[0])
        assertTrue(middleLines[1].startsWith("[done]"))
        assertTrue(middleLines[2].startsWith("[next]"))
        assertTrue(middle.contains("Try This Change: \"add an InviteReminder entity\""))
        assertTrue(middle.contains("InviteReminder linked from Invite"))
        assertTrue(middle.contains("Review Approved Changes -> expect Blueprint to approve the reviewed code patch before apply."))
        assertTrue(middle.indexOf("Generate Code Diff -> expect a reviewed diff") < middle.indexOf("Review Approved Changes -> expect Blueprint to approve the reviewed code patch before apply."))
        assertTrue(middle.indexOf("Review Approved Changes -> expect Blueprint to approve the reviewed code patch before apply.") < middle.indexOf("Apply Approved Changes -> expect the imported invite patch to be written to disk after review approval."))

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
                appliedReady = false,
                refreshedCodeMapReady = false,
                promptReady = false,
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
                appliedReady = false,
                refreshedCodeMapReady = false,
                promptReady = true,
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
                appliedReady = false,
                refreshedCodeMapReady = false,
                promptReady = true,
            ),
        )
        assertTrue(reset.contains("Guided demo prompt loaded: \"restore blueprint_demo/imported_invite/models.py from git\"."))
        assertTrue(reset.contains("restoring blueprint_demo/imported_invite/models.py"))
        assertTrue(reset.contains("Your own change:"))
    }

    @Test
    fun `fresh guided prompt copy stays discoverable in ui source`() {
        val source = java.nio.file.Files.readString(java.nio.file.Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("use Try This Change for a fresh prompt based on the current sandbox state"))
        assertTrue(source.contains("Blueprint keeps this Try This Change prompt fresh by checking the current UML and imported invite code before suggesting the next demo change."))
        assertTrue(source.contains("load the current code map first so Blueprint can choose a fresh demo change"))
        assertTrue(source.contains("Fresh demo prompt: "))
        assertTrue(source.contains("Fresh demo prompt loaded into chat:"))
        assertTrue(source.contains("Try This Change -> use the button to load the fresh prompt into chat first."))
    }
}
