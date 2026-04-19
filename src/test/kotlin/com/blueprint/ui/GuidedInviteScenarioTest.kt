package com.blueprint.ui

import com.blueprint.service.PythonProjectAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(firstLines[1].startsWith("Freshness:"))
        assertTrue(firstLines[2].startsWith("Prompt state:"))
        assertTrue(first.contains("load the current code map first so Blueprint can choose a fresh demo change"))
        assertTrue(first.contains("Try This Change -> load a fresh prompt for the current code map."))
        assertTrue(first.contains("Blueprint reviews the code patch before apply."))
        assertTrue(first.contains("Apply Approved Changes -> blocked until review approves the reviewed code patch."))
        assertTrue(first.contains("Run the changed app -> Blueprint could not infer a run command yet because it did not find a clear runnable entry file. Try this fallback:"))
        assertTrue(first.contains("1. Refresh UML From Code after you pick the Python folder you want to verify."))
        assertTrue(first.contains("2. Look for likely entry files such as __main__.py, app.py, main.py, or a package root."))
        assertTrue(first.contains("4. Confirm the changed feature exists."))
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
        assertTrue(middleLines[1].startsWith("Freshness:"))
        assertTrue(middleLines[2].startsWith("Prompt state:"))
        assertTrue(middleLines[3].startsWith("[done]"))
        assertTrue(middleLines[4].startsWith("[next]"))
        assertTrue(middle.contains("Try This Change: \"add an InviteReminder entity\" -> loaded fresh prompt for the current code map; expect InviteReminder linked from Invite in the UML draft."))
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
        assertTrue(loadedPrompt.contains("Prompt state: the next guided prompt will be fresh for the current code map when you click Try This Change because Blueprint will choose it from the latest Refresh UML From Code result."))
        assertTrue(loadedPrompt.contains("Try This Change -> use the button to load the fresh prompt into chat first."))
        assertTrue(loadedPrompt.contains("Generate Code Diff -> expect a reviewed code patch for blueprint_demo/imported_invite/models.py."))

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
                prompt = "reset blueprint_demo/imported_invite/models.py to the demo baseline",
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
        assertTrue(reset.contains("Freshness: this guided prompt already appears in blueprint_demo/imported_invite/models.py, so Reset Demo Sandbox is recommended to remove that existing change before a predictable fresh demo run."))
        assertTrue(reset.contains("Guided demo prompt loaded: \"reset blueprint_demo/imported_invite/models.py to the demo baseline\"."))
        assertTrue(reset.contains("Reset Demo Sandbox is optional but recommended here because blueprint_demo/imported_invite/models.py already contains the guided change and reset restores the baseline invite demo file for a predictable fresh demo run."))
        assertTrue(reset.contains("If you skip reset, make a different UML-backed change instead of reusing the guided change that is already on disk."))
        assertTrue(reset.contains("After reset, click Try This Change to load a fresh prompt for the clean sandbox."))
        assertTrue(reset.contains("Generate Code Diff -> wait until the sandbox is reset or you choose your own new UML change."))
        assertTrue(reset.contains("Blueprint reviews the fresh code patch before apply."))
        assertTrue(reset.contains("Apply Approved Changes -> blocked until review approves the fresh reviewed code patch."))
        assertTrue(reset.indexOf("Generate Code Diff -> wait until the sandbox is reset or you choose your own new UML change.") < reset.indexOf("Blueprint reviews the fresh code patch before apply."))
        assertTrue(reset.indexOf("Blueprint reviews the fresh code patch before apply.") < reset.indexOf("Apply Approved Changes -> blocked until review approves the fresh reviewed code patch."))
        assertTrue(reset.contains("Your own change:"))
    }


    @Test
    fun `generic first run checklist covers fresh folder patch ready apply complete and manual verification`() {
        val fresh = FirstRunChecklistState(
            codeMapReady = false,
            reviewedDiffReady = false,
            reviewApprovedReady = false,
            appliedReady = false,
            refreshedCodeMapReady = false,
            runCommand = null,
            validationCommand = null,
            validationReady = false,
            validationPassed = false,
            runVerified = false,
        ).checklistText()
        assertTrue(fresh.contains("First-run checklist:"))
        assertTrue(fresh.contains("Blueprint readiness: run not inferred; validation not inferred."))
        assertTrue(fresh.contains("Run readiness: no runnable Python entrypoint inferred yet."))
        assertTrue(fresh.contains("Blueprint could not infer a run command yet because it did not find a clear runnable entry file."))
        assertTrue(fresh.contains("[next] Refresh UML From Code -> load the current Python project into a code-backed UML diagram."))
        assertTrue(fresh.contains("Blueprint could not infer a run command yet because it did not find a clear runnable entry file. Try this fallback:"))
        assertTrue(fresh.contains("1. Refresh UML From Code after you pick the Python folder you want to verify."))
        assertTrue(fresh.contains("2. Look for likely entry files such as __main__.py, app.py, main.py, or a package root."))
        assertTrue(fresh.contains("4. Confirm the changed feature exists."))
        assertTrue(fresh.contains("Validation readiness: not inferred yet."))

        val patchReady = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = true,
            reviewApprovedReady = false,
            appliedReady = false,
            refreshedCodeMapReady = false,
            runCommand = "python main.py",
            runEntryCandidates = listOf("app/main.py", "app.py", "manage.py"),
            validationCommand = "pytest",
            validationReady = false,
            validationPassed = false,
            runVerified = false,
            skippedFiles = listOf(
                PythonProjectAnalyzer.SkippedFile("generated/schema.py", "generated or cache file"),
                PythonProjectAnalyzer.SkippedFile("build/tmp.py", "generated or cache file"),
                PythonProjectAnalyzer.SkippedFile("notes.txt.py", "unsupported or non-importable Python file"),
            ),
        ).checklistText()
        assertTrue(patchReady.contains("Blueprint readiness: run ready; validation ready."))
        assertTrue(patchReady.contains("Run readiness: ready. Blueprint inferred python main.py for this project."))
        assertTrue(patchReady.contains("Run decision: Blueprint inferred this as the best default run command because the current Python folder looks runnable and includes likely entry files such as app/main.py, app.py, manage.py. Recommended command: python main.py. Other likely entry files: app.py, manage.py."))
        assertTrue(patchReady.contains("[done] Generate Code Diff -> create a reviewed code patch from your UML edits."))
        assertTrue(patchReady.contains("- Scope note: 3 Python paths were skipped during Refresh UML From Code (2 generated or cache file, unsupported or non-importable Python file). Blueprint still built the current UML from the Python files it could read, so inspect skipped paths like generated/schema.py, build/tmp.py if anything looks incomplete. Fix the folder or files if needed, then Refresh UML From Code again before Generate Code Diff."))
        assertTrue(patchReady.contains("[next] Review approved -> confirm Blueprint says the reviewed code patch is safe to apply."))
        assertTrue(patchReady.contains("Validation readiness: ready. Blueprint will validate after apply with: pytest"))

        val applied = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = true,
            reviewApprovedReady = true,
            appliedReady = true,
            refreshedCodeMapReady = true,
            runCommand = "python main.py",
            validationCommand = "pytest",
            validationReady = true,
            validationPassed = true,
            runVerified = true,
        ).checklistText()
        assertTrue(applied.contains("Freshness: the code-backed UML is refreshed from the current files on disk."))
        assertTrue(applied.contains("Blueprint readiness: run verified; validation passed after apply."))
        assertTrue(applied.contains("Run readiness: verified. Blueprint verified python main.py for this project."))
        assertTrue(applied.contains("[done] Apply Approved Changes -> write the approved code patch to disk."))
        assertTrue(applied.contains("[done] Refresh UML From Code -> verify the code-backed UML after apply."))
        assertTrue(applied.contains("[done] Run the changed app -> Run verified with: python main.py"))
        assertTrue(applied.contains("Validation readiness: passed after apply with: pytest"))

        val noRunCommand = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = true,
            reviewApprovedReady = true,
            appliedReady = true,
            refreshedCodeMapReady = true,
            runCommand = null,
            validationCommand = null,
            validationReady = true,
            validationPassed = false,
            runVerified = false,
        ).checklistText()
        assertTrue(noRunCommand.contains("Freshness: the code-backed UML is refreshed from the current files on disk."))
        assertTrue(noRunCommand.contains("Run readiness: no runnable Python entrypoint inferred yet."))
        assertTrue(noRunCommand.contains("[next] Run the changed app -> Blueprint could not infer a run command yet because it did not find a clear runnable entry file. Try this fallback:"))
        assertTrue(noRunCommand.contains("1. Refresh UML From Code after you pick the Python folder you want to verify."))
        assertTrue(noRunCommand.contains("2. Look for likely entry files such as __main__.py, app.py, main.py, or a package root."))
        assertTrue(noRunCommand.contains("4. Confirm the changed feature exists."))
        assertTrue(noRunCommand.contains("Validation readiness: ran after apply. Review the result before you continue."))
    }

    @Test
    fun `demo receipt summarizes pass wait states and expected visible results`() {
        val waiting = GuidedInviteScenarioState(
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
        ).demoReceiptText()
        assertTrue(waiting.contains("Demo receipt:"))
        assertTrue(waiting.contains("[next] Refresh UML From Code -> expected visible result: current code map is loaded."))
        assertTrue(waiting.contains("[wait] Try This Change -> load the current code map first."))
        assertTrue(waiting.contains("[wait] Run the changed app -> wait for an inferred run command, then verify the feature manually."))

        val passed = GuidedInviteScenarioState(
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
        assertTrue(passed.contains("[pass] Refresh UML From Code -> expected visible result: current code map is loaded."))
        assertTrue(passed.contains("[pass] Try This Change -> loaded fresh prompt for the current code map: add an InviteReminder entity"))
        assertTrue(passed.contains("[pass] Apply Approved Changes -> expected visible result: code files are written to disk."))
        assertTrue(passed.contains("[pass] Run the changed app -> verified with: python main.py"))
        assertFalse(passed.contains("[wait]"))
    }

    @Test
    fun `reset state switches button copy to reset guidance`() {
        val source = java.nio.file.Files.readString(java.nio.file.Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("firstRunPromptButton.text = if (state.resetSuggested) \"Reset Demo Sandbox\" else \"Try This Change\""))
        assertTrue(source.contains("runDemoButton.text = if (state.runVerified) \"Demo Run Verified\" else \"Run Demo Step\""))
        assertTrue(source.contains("demoReceiptArea.text = state.demoReceiptText()"))
        assertTrue(source.contains("The guided demo prompt likely matches code that is already in \${state.resetPath}. Reset Demo Sandbox is optional but recommended because that file already contains the guided change. If you skip reset, make a different UML-backed change instead."))
        assertTrue(source.contains("Reset Demo Sandbox restored \${state.resetPath} to the baseline invite demo file and removed the guided change that was already there. Refresh UML From Code next, then click Try This Change to load a fresh prompt. If you skip reset later, make a different UML-backed change instead."))
    }

    @Test
    fun `fresh guided prompt copy stays available outside the main workflow surface`() {
        val source = java.nio.file.Files.readString(java.nio.file.Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertFalse(source.contains("use Try This Change for a fresh prompt based on the current sandbox state"))
        assertTrue(source.contains("Reset Demo Sandbox restored \${state.resetPath} to the baseline invite demo file and removed the guided change that was already there. Refresh UML From Code next, then click Try This Change to load a fresh prompt. If you skip reset later, make a different UML-backed change instead."))
        assertTrue(source.contains("load the current code map first so Blueprint can choose a fresh demo change"))
        assertTrue(source.contains("Freshness: this guided prompt already appears in \${state.resetPath}, so Reset Demo Sandbox is recommended to remove that existing change before a predictable fresh demo run."))
        assertTrue(source.contains("Freshness: Try This Change will load a prompt chosen from the current code map so the guided demo starts from the current sandbox state. That keeps the suggested change aligned with what Refresh UML From Code just found, not an older canned demo step."))
        assertTrue(source.contains("Prompt state: the suggested guided prompt is fresh for the current code map because it was chosen from the latest Refresh UML From Code result."))
        assertTrue(source.contains("Prompt state: the next guided prompt will be fresh for the current code map when you click Try This Change because Blueprint will choose it from the latest Refresh UML From Code result."))
        assertTrue(source.contains("Fresh prompt for the current sandbox:"))
        assertTrue(source.contains("Fresh prompt already loaded for the current sandbox:"))
        assertTrue(source.contains("Fresh prompt loaded for the current sandbox:"))
        assertTrue(source.contains("Demo e2e step passed: Try This Change prepared"))
        assertTrue(source.contains("Manual demo runner"))
        assertTrue(source.contains("Demo receipt:"))
        assertTrue(source.contains("Run Demo Step"))
        assertTrue(source.contains("Try This Change -> use the button to load the fresh prompt into chat first."))
    }
}
