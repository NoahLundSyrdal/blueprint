package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RunDemoChecklistRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `manual demo runner includes a what to look for checklist`() {
        assertTrue(source.contains("private fun manualDemoWhatToLookForChecklist(state: GuidedInviteScenarioState): String ="))
        assertTrue(source.contains("What to look for:"))
        assertTrue(source.contains("- The visible result matches the reviewed code patch you just applied."))
    }

    @Test
    fun `manual demo runner checklist adapts to guided invite relation prompts`() {
        assertTrue(source.contains("- ${'$'}{state.expectedRelationSource} shows ${'$'}{state.expectedEntity} in the refreshed UML."))
        assertTrue(source.contains("- The changed app flow shows the new ${'$'}{state.expectedEntity} behavior."))
    }

    @Test
    fun `manual demo runner checklist adapts to expires_at prompt`() {
        assertTrue(source.contains("- Invite shows expires_at in the refreshed UML."))
        assertTrue(source.contains("- The changed app path shows the new expires_at behavior."))
    }
}
