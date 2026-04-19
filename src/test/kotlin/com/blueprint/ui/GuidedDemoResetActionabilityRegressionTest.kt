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

        assertTrue(source.contains("[next] Reset Demo Sandbox is optional but recommended here because it restores \${state.resetPath} to the baseline invite demo file for a predictable fresh demo run."))
        assertTrue(source.contains("[next] After reset, click Try This Change to load a fresh prompt for the clean sandbox."))
        assertTrue(source.contains("Reset Demo Sandbox restored \${state.resetPath} to the baseline invite demo file. Refresh UML From Code next, then click Try This Change to load a fresh prompt. If you skip reset later, make your own UML-backed change instead."))
        assertTrue(source.contains("The guided demo prompt likely matches code already in the invite demo file. Reset Demo Sandbox is optional but recommended for a predictable fresh run. If you skip reset, make your own UML-backed change instead."))
        assertTrue(source.contains("The guided demo prompt likely matches code that is already in \${state.resetPath}. Reset Demo Sandbox is optional but recommended for a predictable fresh invite demo run. If you skip reset, make your own UML-backed change instead."))
    }
}
