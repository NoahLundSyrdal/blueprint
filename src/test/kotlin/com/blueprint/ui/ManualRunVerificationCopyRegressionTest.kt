package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ManualRunVerificationCopyRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `manual verification copy reuses likely entry file guidance`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun missingRunCommandGuidance("))
        assertTrue(source.contains("Run the changed app -> ${'$'}{missingRunCommandChecklist(emptyList())}"))
        assertTrue(source.contains("No run command was inferred. Use Open Likely Entry File to inspect the best candidate, then try the other likely entry files from the checklist if needed."))
        assertTrue(source.contains("Try this fallback:"))
        assertTrue(source.contains("private val openLikelyEntryFileButton = JButton(\"Open Likely Entry File\").apply {"))
    }
}
