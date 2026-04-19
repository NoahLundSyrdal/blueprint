package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RunDisabledStateCopyRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `run controls show visible disabled-state explanation`() {
        assertTrue(source.contains("runStatusNoteLabel.text = disabledRunExplanation(context, runCommand)"))
        assertTrue(source.contains("private val runStatusNoteLabel = JLabel().apply {"))
        assertTrue(source.contains("private fun disabledRunExplanation("))
        assertTrue(source.contains("Run readiness: no runnable Python entrypoint inferred yet. Next: Refresh UML From Code, then inspect a likely entry file manually if needed."))
        assertTrue(source.contains("Run readiness: Blueprint found likely entry files but no single safe default command yet. Next: Open Likely Entry File or Refresh UML From Code if you changed folders."))
        assertTrue(source.contains("Run readiness: ready. Click Run In Blueprint to launch \$runCommand."))
    }
}
