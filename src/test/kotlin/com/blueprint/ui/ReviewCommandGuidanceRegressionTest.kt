package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewCommandGuidanceRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `review area shows inferred validation and run commands before apply`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun validationCommandReviewText(command: String?): String ="))
        assertTrue(source.contains("\"Validation after apply will run: "))
        assertTrue(source.contains("\"Validation after apply: no command inferred yet, so verify manually if you need extra checks.\""))
        assertTrue(source.contains("private fun runCommandReviewText(context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze()): String ="))
        assertTrue(source.contains("\"Run after apply with: "))
        assertTrue(source.contains("\"Run after apply: Blueprint could not infer a command yet, so open the project entrypoint manually to verify the feature.\""))
        assertTrue(source.contains("private fun commandReviewBlock("))
        assertTrue(source.contains("\"Before apply, Blueprint expects:\""))
        assertTrue(source.contains("!exec?.patches.isNullOrEmpty() -> commandBlock"))
    }
}
