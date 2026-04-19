package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewCommandGuidanceRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `review guidance reuses likely entry file fallback when no run command is inferred`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun runCommandReviewText(context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze()): String ="))
        assertTrue(source.contains("Run after apply is unavailable. "+"${'$'}{missingRunCommandGuidance(context)}"))
        assertTrue(source.contains("Open one of these likely entry files manually and verify the changed feature exists"))
    }
}
