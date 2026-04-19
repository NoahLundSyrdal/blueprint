package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ManualDemoRunnerPromptRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `manual demo runner derives expected visible result from guided prompt state`() {
        assertTrue(source.contains("private fun manualDemoExpectedVisibleResult(state: GuidedInviteScenarioState): String ="))
        assertTrue(source.contains("state.expectedEntity == \"Invite\" && state.prompt.contains(\"expires_at\")"))
        assertTrue(source.contains("Expect Invite to show expires_at in the refreshed UML and in the running feature path."))
        assertTrue(source.contains("Expect \${state.expectedRelationSource} to show \${state.expectedEntity} in the refreshed UML and the running app flow."))
        assertTrue(source.contains("val expectedVisibleResult = manualDemoExpectedVisibleResult(state)"))
    }

    @Test
    fun `manual demo runner explains reset path before suggesting a fresh prompt`() {
        assertTrue(source.contains("state.resetSuggested ->"))
        assertTrue(source.contains("Click Reset Demo Sandbox to restore the invite demo file, refresh UML from code, then click Try This Change again so Blueprint can load a fresh prompt for the clean sandbox state."))
    }
}
