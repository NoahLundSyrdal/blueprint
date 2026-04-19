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
        assertTrue(source.contains("Open one of these likely entry files manually and verify the changed feature exists"))
        assertTrue(source.contains("Blueprint looked for FastAPI, Flask, Streamlit, __main__.py, app.py, main.py, and __name__ == \\\"__main__\\\" entrypoints."))
        assertTrue(source.contains("Run after apply is unavailable. "+"${'$'}{missingRunCommandGuidance(context)}"))
    }
}
