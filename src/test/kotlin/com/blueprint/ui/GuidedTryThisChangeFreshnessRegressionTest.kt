package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class GuidedTryThisChangeFreshnessRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `guided try this change copy explains freshness and reset clearly`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("[next] Try This Change -> load a fresh prompt for the current code map."))
        assertTrue(source.contains("[pass] Try This Change -> loaded fresh prompt for the current code map: "))
        assertTrue(source.contains("loaded fresh prompt for the current code map;"))
        assertTrue(source.contains("Freshness: this guided prompt already appears in \${state.resetPath}, so Reset Demo Sandbox is recommended to remove that existing change before a predictable fresh demo run."))
        assertTrue(source.contains("Freshness: Try This Change will load a prompt chosen from the current code map so the guided demo starts from the current sandbox state. That keeps the suggested change aligned with what Refresh UML From Code just found, not an older canned demo step."))
        assertTrue(source.contains("Prompt state: Refresh UML From Code first so Blueprint can choose a fresh demo prompt for the current sandbox."))
        assertTrue(source.contains("Prompt state: the suggested guided prompt is fresh for the current code map because it was chosen from the latest Refresh UML From Code result."))
        assertTrue(source.contains("Prompt state: the next guided prompt will be fresh for the current code map when you click Try This Change because Blueprint will choose it from the latest Refresh UML From Code result."))
        assertTrue(source.contains("Fresh prompt for the current sandbox:"))
        assertTrue(source.contains("Fresh prompt already loaded for the current sandbox:"))
        assertTrue(source.contains("Fresh prompt loaded for the current sandbox:"))
        assertTrue(source.contains("Send it as-is, or edit it before Generate Code Diff."))
        assertTrue(source.contains("Reset Demo Sandbox restored \${state.resetPath} to the baseline invite demo file and removed the guided change that was already there. Refresh UML From Code next, then click Try This Change to load a fresh prompt. If you skip reset later, make a different UML-backed change instead."))
        assertTrue(source.contains("The guided demo prompt likely matches code that is already in \${state.resetPath}. Reset Demo Sandbox is optional but recommended because that file already contains the guided change. If you skip reset, make a different UML-backed change instead."))
    }
}
