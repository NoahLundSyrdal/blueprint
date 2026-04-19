package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class NoRunCommandFallbackRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `manual fallback checklist gives ordered likely-entry guidance`() {
        assertTrue(source.contains("Blueprint could not infer a run command yet because none of the likely entry files mapped to a single safe default command. Try this fallback:"))
        assertTrue(source.contains("1. Open Likely Entry File to inspect the best candidate."))
        assertTrue(source.contains("2. If that is not the right launcher, try one of these likely entry files: \$candidateList"))
        assertTrue(source.contains("3. Run the best candidate from the IDE or terminal."))
        assertTrue(source.contains("4. Confirm the changed feature exists in the running app or CLI output."))
    }

    @Test
    fun `no-candidate fallback still explains what to look for`() {
        assertTrue(source.contains("Blueprint could not infer a run command yet because it did not find a clear runnable entry file. Try this fallback:"))
        assertTrue(source.contains("1. Refresh UML From Code after you pick the Python folder you want to verify."))
        assertTrue(source.contains("2. Look for likely entry files such as __main__.py, app.py, main.py, or a package root."))
        assertTrue(source.contains("3. Open the best candidate and run it from the IDE or terminal."))
        assertTrue(source.contains("4. Confirm the changed feature exists."))
    }

    @Test
    fun `no-run-command tooltips point to likely-entry fallback instead of dead-end messaging`() {
        assertTrue(source.contains("No run command was inferred yet. Use Open Likely Entry File to inspect the best candidate, run it manually, confirm the feature, then Refresh UML From Code if you changed folders."))
        assertTrue(source.contains("No run command was inferred yet. Refresh UML From Code, inspect a likely entry file manually, run it, and confirm the feature."))
    }
}
