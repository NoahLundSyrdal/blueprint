package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class FirstRunChecklistRunActionRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `changed app run action stays in the main workflow controls`() {
        assertTrue(source.contains("private val runChecklistActionButton = JButton(\"Run The Changed App\")"))
        assertTrue(source.contains("add(runChecklistActionButton)"))
        assertTrue(source.contains("toolTipText = \"Run the inferred project command from the first-run checklist when one is available.\""))
        assertTrue(source.contains("runChecklistActionButton.text = if (state.runVerified) \"Run The Changed App Again\" else \"Run The Changed App\""))
        assertTrue(source.contains("runChecklistActionButton.text = if (genericState.runVerified) \"Run The Changed App Again\" else \"Run The Changed App\""))
    }

    @Test
    fun `changed app run action keeps copy honest when no run command is inferred`() {
        assertTrue(source.contains("runChecklistActionButton.isEnabled = state.codeMapReady && !state.runCommand.isNullOrBlank()"))
        assertTrue(source.contains("runChecklistActionButton.isEnabled = genericState.codeMapReady && !genericState.runCommand.isNullOrBlank()"))
        assertTrue(source.contains("Refresh UML From Code first so Blueprint can infer a run command for the current project."))
        assertTrue(source.contains("No run command was inferred yet. Use Open Likely Entry File to inspect the best candidate, run it manually, confirm the feature, then Refresh UML From Code if you changed folders."))
    }

    @Test
    fun `checklist run action logs the launch so activity still reads like a receipt`() {
        assertTrue(source.contains("private fun runFromChecklist() {"))
        assertTrue(source.contains("logActivity(\"First-run checklist run blocked: no inferred project run command was available.\")"))
        assertTrue(source.contains("logActivity(\"First-run checklist run launched: \$runCommand\")"))
        assertTrue(source.contains("status(\"Launching run from checklist\")"))
        assertTrue(source.contains("toggleRunInBlueprint()"))
        assertTrue(source.contains("runChecklistActionButton.text = \"Stop The Changed App\""))
        assertTrue(source.contains("runChecklistActionButton.toolTipText = \"Stop the checklist run that is streaming inside Blueprint.\""))
        assertTrue(source.contains("runChecklistActionButton.toolTipText = runCommand?.let { \"Run the inferred project command from the first-run checklist: \$it\" }"))
    }
}
