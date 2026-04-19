package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ManualRunFallbackCardRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `run controls include a dedicated manual fallback card near run actions`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private val manualRunFallbackCard = RoundedSurfacePanel(BorderLayout(0, 6), BlueprintTheme.WarningSurface, BlueprintTheme.Warning).apply {"))
        assertTrue(source.contains("add(JLabel(\"Manual run fallback\")"))
        assertTrue(source.contains("text = missingRunCommandChecklist(emptyList())"))
        assertTrue(source.contains("add(manualRunFallbackCard.apply {"))
        assertTrue(source.contains("add(runAppButton)"))
        assertTrue(source.contains("add(openLikelyEntryFileButton)"))
    }

    @Test
    fun `manual fallback card explains automatic vs manual run steps and likely entry action`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun updateManualRunFallbackCard("))
        assertTrue(source.contains("if (!runCommand.isNullOrBlank()) {"))
        assertTrue(source.contains("manualRunFallbackCard.isVisible = false"))
        assertTrue(source.contains("Blueprint could not run this app automatically yet."))
        assertTrue(source.contains("Manual verification path: inspect the likely entry file, run it manually, confirm the feature, then Refresh UML From Code if you changed folders."))
        assertTrue(source.contains("What Blueprint can do now: Refresh UML From Code re-checks the current Python folder, and Open Likely Entry File opens the strongest launcher candidate in the IDE."))
        assertTrue(source.contains("append(missingRunCommandChecklist(context.runEntryCandidates))"))
        assertTrue(source.contains("Likely entry file action: Open Likely Entry File (\${firstCandidate.substringAfterLast('/')}) opens \$firstCandidate in the IDE without running or applying anything."))
        assertTrue(source.contains("updateManualRunFallbackCard(context, runCommand)"))
    }
}
