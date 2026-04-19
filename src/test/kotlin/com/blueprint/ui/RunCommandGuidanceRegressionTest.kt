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

        assertTrue(source.contains("private fun inferredRunCommandReason(runCommand: String, runEntryCandidates: List<String>): String"))
        assertTrue(source.contains("private fun missingRunCommandGuidance("))
        assertTrue(source.contains("private fun missingRunCommandChecklist(runEntryCandidates: List<String>): String"))
        assertTrue(source.contains("Blueprint could not infer a run command yet because it did not find a clear runnable entry file."))
        assertTrue(source.contains("Blueprint could not infer a run command yet because none of the likely entry files mapped to a single safe default command."))
        assertTrue(source.contains("Try this fallback:"))
        assertTrue(source.contains("1. Refresh UML From Code after you pick the Python folder you want to verify."))
        assertTrue(source.contains("2. Look for likely entry files such as __main__.py, app.py, main.py, or a package root."))
        assertTrue(source.contains("1. Open Likely Entry File to jump into the best candidate."))
        assertTrue(source.contains("val candidateList = candidates.joinToString(\", \")"))
        assertTrue(source.contains("2. If that is not the right launcher, try one of these likely entry files: ${'$'}candidateList"))
        assertTrue(source.contains("6. Search for FastAPI, Flask, Streamlit, __main__.py, app.py, main.py, or __name__ == \\\"__main__\\\"."))
        assertTrue(source.contains("When you want to run the app, start with: ") && source.contains("or click Run In Blueprint. ${'$'}{inferredRunCommandReason(it, context.runEntryCandidates)}"))
        assertTrue(source.contains("Run after apply was inferred automatically: ") && source.contains(". ${'$'}{inferredRunCommandReason(it, context.runEntryCandidates)}"))
        assertTrue(source.contains("Run after apply is unavailable. "+"${'$'}{missingRunCommandGuidance(context)}"))
        assertTrue(source.contains("private fun updateLikelyEntryFileAction("))
        assertTrue(source.contains("private fun openLikelyEntryFile() {"))
        assertTrue(source.contains("Open likely entry file: ${'$'}it. This does not run or apply anything."))
    }
}
