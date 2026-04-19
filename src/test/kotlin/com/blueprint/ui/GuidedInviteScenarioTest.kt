package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedInviteScenarioTest {
    @Test
    fun `matches bundled invite project by name or path`() {
        assertTrue(GuidedInviteScenario.matchesProject("invite_project", null))
        assertTrue(GuidedInviteScenario.matchesProject("anything", "/tmp/work/examples/invite_project"))
        assertFalse(GuidedInviteScenario.matchesProject("car_company_project", "/tmp/work/examples/car_company_project"))
    }

    @Test
    fun `checklist advances through deterministic invite demo steps`() {
        val first = GuidedInviteScenario.checklistText(
            GuidedInviteScenarioState(
                codeMapReady = false,
                umlDraftReady = false,
                reviewedDiffReady = false,
                appliedReady = false,
                refreshedCodeMapReady = false,
            ),
        )
        val firstLines = first.lines()
        assertTrue(firstLines[0].startsWith("[next]"))
        assertTrue(firstLines.drop(1).all { it.startsWith("[wait]") })

        val middle = GuidedInviteScenario.checklistText(
            GuidedInviteScenarioState(
                codeMapReady = true,
                umlDraftReady = true,
                reviewedDiffReady = false,
                appliedReady = false,
                refreshedCodeMapReady = false,
            ),
        )
        val middleLines = middle.lines()
        assertTrue(middleLines[0].startsWith("[done]"))
        assertTrue(middleLines[1].startsWith("[done]"))
        assertTrue(middleLines[2].startsWith("[next]"))

        val complete = GuidedInviteScenario.checklistText(
            GuidedInviteScenarioState(
                codeMapReady = true,
                umlDraftReady = true,
                reviewedDiffReady = true,
                appliedReady = true,
                refreshedCodeMapReady = true,
            ),
        )
        assertTrue(complete.lines().all { it.startsWith("[done]") })
        assertTrue(complete.contains(GuidedInviteScenario.PROMPT))
        assertTrue(complete.contains(GuidedInviteScenario.PATCH_PATH))
    }
}
