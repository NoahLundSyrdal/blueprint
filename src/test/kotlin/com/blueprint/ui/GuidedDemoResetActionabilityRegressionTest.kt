package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class GuidedDemoResetActionabilityRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `no fresh demo scenario explains the reset action and restored file`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("[next] Click Reset Demo Sandbox to restore \${state.resetPath} to the baseline invite demo file."))
        assertTrue(source.contains("[next] Then click Try This Change to load a fresh prompt for the clean sandbox."))
        assertTrue(source.contains("Reset Demo Sandbox restored \${state.resetPath} to the baseline invite demo file. Refresh UML From Code, then click Try This Change for a fresh prompt. You can still skip the reset and make your own UML edit instead."))
        assertTrue(source.contains("The guided demo prompt likely matches code already in the invite demo file. Click Reset Demo Sandbox for a fresh run, or keep your own UML edit instead."))
        assertTrue(source.contains("The guided demo prompt likely matches code that is already in \${state.resetPath}. Click Reset Demo Sandbox for a fresh invite demo run, or keep your own UML edit instead."))
    }
}
