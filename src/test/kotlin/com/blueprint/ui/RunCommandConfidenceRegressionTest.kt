package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RunCommandConfidenceRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `post run summary explains why the current run command remains the default`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun verifiedRunCommandReason(runCommand: String, runEntryCandidates: List<String>): String"))
        assertTrue(source.contains("\"Run decision: Blueprint verified \$runCommand and did not detect competing entry files, so it remains the recommended default.\""))
        assertTrue(source.contains("\"Run decision: Blueprint verified \$runCommand against the strongest entry file signal (\${candidates.first()}), so it remains the recommended default.\""))
        assertTrue(source.contains("\"Run decision: Blueprint verified \$runCommand against the strongest entry-file signals (\${candidates.joinToString(\", \")}), so it remains the recommended default for now.\""))
        assertTrue(source.contains("val alternatives = runCommandAlternatives(runEntryCandidates)"))
        assertTrue(source.contains("val runConfidence = verifiedRunCommandReason(runCommand, pythonContext.runEntryCandidates)"))
        assertTrue(source.contains("\"- \$runConfidence\""))
        assertTrue(source.contains("appendLine(\"- \$runConfidence\")"))
    }
}
