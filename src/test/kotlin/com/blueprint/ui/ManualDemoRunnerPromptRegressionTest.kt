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
        assertTrue(source.contains("Expect Invite to show expires_at in the refreshed UML and in the running app, browser, or terminal flow."))
        assertTrue(source.contains("Expect \${state.expectedRelationSource} to show \${state.expectedEntity} in the refreshed UML and in the running app, browser, or terminal flow."))
        assertTrue(source.contains("val expectedVisibleResult = manualDemoExpectedVisibleResult(state)"))
    }

    @Test
    fun `manual demo runner explains reset path before suggesting a fresh prompt`() {
        assertTrue(source.contains("state.resetSuggested ->"))
        assertTrue(source.contains("The guided demo prompt likely matches code already in the invite demo file. Click Reset Demo Sandbox for a fresh run, or keep your own UML edit instead."))
    }
}
