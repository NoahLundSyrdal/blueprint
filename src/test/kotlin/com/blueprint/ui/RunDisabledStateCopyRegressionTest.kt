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
        assertTrue(source.contains("private fun runReadinessSummary(runCommand: String?, runEntryCandidates: List<String>, runVerified: Boolean = false): String = when {"))
        assertTrue(source.contains("private fun manualVerificationNextStep(runEntryCandidates: List<String>): String ="))
        assertTrue(source.contains("\"\${runReadinessSummary(runCommand, context.runEntryCandidates)} Click Run In Blueprint to launch \$runCommand.\""))
        assertTrue(source.contains("\"\${runReadinessSummary(null, context.runEntryCandidates)} \${manualVerificationNextStep(context.runEntryCandidates)}\""))
    }
}
