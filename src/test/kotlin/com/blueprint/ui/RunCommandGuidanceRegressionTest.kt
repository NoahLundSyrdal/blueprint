package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RunCommandGuidanceRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `manual run guidance names likely entry files and fallback search targets`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun missingRunCommandGuidance("))
        assertTrue(source.contains("private fun missingRunCommandChecklist(runEntryCandidates: List<String>): String"))
        assertTrue(source.contains("Verify manually with this checklist:"))
        assertTrue(source.contains("Open Likely Entry File to jump into one of these likely entry files:"))
        assertTrue(source.contains("- Search for FastAPI, Flask, Streamlit, __main__.py, app.py, main.py, or __name__ == \\\"__main__\\\"."))
        assertTrue(source.contains("Run after apply is unavailable. "+"${'$'}{missingRunCommandGuidance(context)}"))
        assertTrue(source.contains("private fun updateLikelyEntryFileAction("))
        assertTrue(source.contains("private fun openLikelyEntryFile() {"))
        assertTrue(source.contains("Open likely entry file: ${'$'}it. This does not run or apply anything."))
    }
}
