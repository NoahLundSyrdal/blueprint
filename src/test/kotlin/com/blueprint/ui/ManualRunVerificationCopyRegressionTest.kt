package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ManualRunVerificationCopyRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `first run guidance gives a manual verification fallback when no run command is inferred`() {
        assertTrue(source.contains("No run command was inferred. Open the project entrypoint or main screen manually and verify the changed feature exists."))
        assertTrue(source.contains("Blueprint could not infer a run command, so open the project entrypoint or main screen manually and verify the changed feature exists."))
        assertTrue(source.contains("Blueprint could not infer a run command yet. Open the project entrypoint or main screen manually and verify the changed feature exists."))
    }
}
