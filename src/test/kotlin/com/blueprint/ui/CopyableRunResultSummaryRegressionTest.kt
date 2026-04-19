package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class CopyableRunResultSummaryRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `run verification exposes copyable result summary backed by recorded state`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private val copyRunSummaryButton = JButton(\"Copy Issue Comment\")"))
        assertTrue(source.contains("toolTipText = \"Copy an issue-comment-ready recap after run verification or manual verification prep.\""))
        assertTrue(source.contains("add(copyRunSummaryButton)"))
        assertTrue(source.contains("val copyableSummary = runResultSummary(runCommand, visibleResult, demoFlow)"))
        assertTrue(source.contains("\"- Copy Issue Comment if you want a reusable issue-comment or demo recap.\""))
        assertTrue(source.contains("append(copyableSummary)"))
        assertTrue(source.contains("copyRunSummaryButton.isEnabled = true"))
        assertTrue(source.contains("appendLine(\"Copyable issue comment\")"))
        assertTrue(source.contains("appendLine(\"- Run verified with: \$runCommand\")"))
        assertTrue(source.contains("appendLine(\"- Visible result: \$visibleResult\")"))
        assertTrue(source.contains("appendLine(\"- Rerun ready: reuse \$runCommand after the next approved change when you want to confirm the next iteration quickly.\")"))
        assertTrue(source.contains("appendLine(\"- Changed paths: \${if (changedPaths.isEmpty()) \"none\" else changedPaths.joinToString(\", \")}\")"))
        assertTrue(source.contains("append(validationSummary.removePrefix(\"Result summary\\n\"))"))
    }
}
