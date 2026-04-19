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
        assertTrue(source.contains("Reset the invite demo sandbox at \${state.resetPath}. Refresh UML From Code, then use Try This Change to load a fresh prompt for the clean sandbox."))
    }
}
