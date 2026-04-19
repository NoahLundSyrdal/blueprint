package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class VerifyInUmlActionRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `post apply flow exposes Verify In UML action and reread copy`() {
        assertTrue(source.contains("private val verifyInUmlButton = JButton(\"Verify In UML\").apply {"))
        assertTrue(source.contains("toolTipText = \"Refresh UML From Code to reread the changed files from disk and verify the updated code-backed UML after apply.\""))
        assertTrue(source.contains("addActionListener { refreshUmlAfterApplyVerification() }"))
        assertTrue(source.contains("add(verifyInUmlButton)"))
        assertTrue(source.contains("verifyInUmlButton.isEnabled = n.executionStatus == ExecutionStatus.APPLIED"))
        assertTrue(source.contains("verifyInUmlButton.isEnabled = true"))
        assertTrue(source.contains("Blueprint already refreshed the code-backed UML from disk after apply. Refresh UML From Code to verify again whenever you want to rerun that refresh."))
        assertTrue(source.contains("appendLine(\"- Automatic refresh after apply already reread the changed code from disk.\")"))
        assertTrue(source.contains("Refresh UML From Code reruns the refresh when you want an explicit verification click."))
        assertTrue(source.contains("private fun refreshUmlAfterApplyVerification() {"))
        assertTrue(source.contains("logActivity(\"Verify In UML rereads the changed code from disk after apply.\")"))
        assertTrue(source.contains("status(\"Verifying applied changes in UML\")"))
        assertTrue(source.contains("generateProjectUml()"))
    }
}
