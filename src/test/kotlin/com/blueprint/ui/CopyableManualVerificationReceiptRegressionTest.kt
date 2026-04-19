package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class CopyableManualVerificationReceiptRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `copy result summary falls back to manual verification receipt when no run command is inferred`() {
        assertTrue(source.contains("private fun manualVerificationReceipt(context: PythonProjectAnalyzer.PythonProjectContext, demoFlow: Boolean): String"))
        assertTrue(source.contains("Copyable manual verification receipt"))
        assertTrue(source.contains("Blueprint could not infer a project run command yet"))
        assertTrue(source.contains("Refresh UML From Code to verify the current code-backed UML before you inspect the app manually."))
        assertTrue(source.contains("- Changed paths: \${if (changedPaths.isEmpty()) \"none\" else changedPaths.joinToString(\", \")}"))
        assertTrue(source.contains("- Next action: inspect the changed paths and likely entry files side by side until you confirm the feature exists."))
        assertTrue(source.contains("val summary = if (runCommand.isNullOrBlank()) {"))
        assertTrue(source.contains("manualVerificationReceipt(context, false)"))
        assertTrue(source.contains("status(if (runCommand.isNullOrBlank()) \"Copied manual verification receipt\" else \"Copied result summary\")"))
    }

    @Test
    fun `manual verification receipt includes likely entry files when available`() {
        assertTrue(source.contains("val likelyEntryFiles = context.runEntryCandidates.take(3)"))
        assertTrue(source.contains("\"- Likely entry files: none identified yet.\""))
        assertTrue(source.contains("\"- Likely entry files: \${likelyEntryFiles.joinToString(\", \")}.\""))
    }
}
