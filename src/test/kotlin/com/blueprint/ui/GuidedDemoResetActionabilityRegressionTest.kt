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

        assertTrue(source.contains("[next] Reset Demo Sandbox is optional but recommended here because \${state.resetPath} already contains the guided change and reset restores the baseline invite demo file for a predictable fresh demo run."))
        assertTrue(source.contains("[next] After reset, click Try This Change to load a fresh prompt for the clean sandbox."))
        assertTrue(source.contains("Reset Demo Sandbox restored \${state.resetPath} to the baseline invite demo file and removed the guided change that was already there. Refresh UML From Code next, then click Try This Change to load a fresh prompt. If you skip reset later, make a different UML-backed change instead."))
        assertTrue(source.contains("The guided demo prompt likely matches code already in the invite demo file. Reset Demo Sandbox is optional but recommended because the guided change is already on disk. If you skip reset, make a different UML-backed change instead."))
        assertTrue(source.contains("The guided demo prompt likely matches code that is already in \${state.resetPath}. Reset Demo Sandbox is optional but recommended because that file already contains the guided change. If you skip reset, make a different UML-backed change instead."))
    }
}
