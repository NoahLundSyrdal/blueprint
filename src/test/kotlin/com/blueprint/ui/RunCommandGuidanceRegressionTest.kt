package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RunCommandGuidanceRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `apply success and empty state surface inferred run guidance`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val runNote = inferredRunNote(pythonContext)"))
        assertTrue(source.contains("listOf(summaryLine, whatChanged, changedPathsBlock, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, highlightLine, validationBlock)"))
        assertTrue(source.contains("val runGuide = inferredRunGuideText(context)"))
        assertTrue(source.contains("When you want to run the app, start with:"))
        assertTrue(source.contains("Run the changed app with:"))
        assertTrue(source.contains("Blueprint could not infer a run command yet."))
        assertTrue(source.contains("Blueprint could not infer a run command, so open the project entrypoint or main screen manually and verify the changed feature exists."))
        assertTrue(source.contains("Open the project entrypoint or main screen manually and verify the changed feature exists."))
        assertTrue(source.contains("Blueprint looked for FastAPI, Flask, Streamlit, __main__.py, app.py, main.py, and __name__ == \\\"__main__\\\" entrypoints."))
    }
}
