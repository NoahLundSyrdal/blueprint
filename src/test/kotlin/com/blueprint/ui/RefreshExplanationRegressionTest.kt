package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RefreshExplanationRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `refresh messages explain exactly what changed after refresh`() {
        assertTrue(source.contains("private data class RefreshExplanation("))
        assertTrue(source.contains("private fun refreshScopeSummary(context: PythonProjectAnalyzer.PythonProjectContext): String {"))
        assertTrue(source.contains("private fun refreshExplanation("))
        assertTrue(source.contains("Refresh UML From Code reloaded "))
        assertTrue(source.contains("Blueprint refreshed the code-backed UML from the current files on disk."))
        assertTrue(source.contains("Refresh scope: no Python paths were skipped."))
        assertTrue(source.contains("val refreshExplanation = refreshExplanation(generated, context, postApplyHighlightMessage, postApplyVerifyState)"))
        assertTrue(source.contains("append(\"I abstracted the current Python code into UML. "))
        assertTrue(source.contains("refreshExplanation.summary"))
        assertTrue(source.contains("append(\"\\n\\n\""))
        assertTrue(source.contains("refreshExplanation.detail"))
        assertTrue(source.contains("postApplyHighlightMessage = \"Refresh UML From Code will now explain exactly what changed in the refreshed code-backed UML.\""))
    }
}
