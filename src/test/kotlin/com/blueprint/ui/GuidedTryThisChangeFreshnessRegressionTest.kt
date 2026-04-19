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
        assertTrue(source.contains("Fresh prompt for the current sandbox:"))
        assertTrue(source.contains("Fresh prompt already loaded for the current sandbox:"))
        assertTrue(source.contains("Fresh prompt loaded for the current sandbox:"))
        assertTrue(source.contains("Send it as-is, or edit it before Generate Code Diff."))
        assertTrue(source.contains("Reset Demo Sandbox restored \${state.resetPath} to the baseline invite demo file. Refresh UML From Code, then click Try This Change for a fresh prompt. You can still skip the reset and make your own UML edit instead."))
        assertTrue(source.contains("The guided demo prompt likely matches code that is already in \${state.resetPath}. Click Reset Demo Sandbox for a fresh invite demo run, or keep your own UML edit instead."))
    }
}
