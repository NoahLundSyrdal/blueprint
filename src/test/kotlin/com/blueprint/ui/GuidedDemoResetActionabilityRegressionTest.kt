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
        assertTrue(source.contains("Reset Demo Sandbox restored \${state.resetPath} to the baseline invite demo file. Refresh UML From Code, then click Try This Change for a fresh prompt."))
        assertTrue(source.contains("Click Reset Demo Sandbox to restore the invite demo file, refresh UML from code, then click Try This Change again so Blueprint can load a fresh prompt for the clean sandbox state."))
        assertTrue(source.contains("Click Reset Demo Sandbox to restore \${state.resetPath} to the baseline invite demo file, or pick your own change."))
    }
}
