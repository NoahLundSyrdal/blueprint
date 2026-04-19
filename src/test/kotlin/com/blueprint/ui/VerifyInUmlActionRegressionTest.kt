package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class VerifyInUmlActionRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `post apply flow uses refresh uml wording for verification action`() {
        assertTrue(source.contains("private val verifyInUmlButton = JButton(\"Refresh UML From Code\").apply {"))
        assertTrue(source.contains("toolTipText = \"Rerun Refresh UML From Code after apply so you can verify the changed files in the code-backed UML again.\""))
        assertTrue(source.contains("addActionListener { refreshUmlAfterApplyVerification() }"))
        assertTrue(source.contains("add(verifyInUmlButton)"))
        assertTrue(source.contains("verifyInUmlButton.isEnabled = n.executionStatus == ExecutionStatus.APPLIED"))
        assertTrue(source.contains("verifyInUmlButton.isEnabled = true"))
        assertTrue(source.contains("Blueprint already refreshed the code-backed UML from disk after apply. Refresh UML From Code to verify again whenever you want to rerun that refresh."))
        assertTrue(source.contains("appendLine(\"- Blueprint already reloaded the changed code into the UML after apply.\")"))
        assertTrue(source.contains("Click Refresh UML From Code when you want to verify that reload yourself."))
        assertTrue(source.contains("private fun refreshUmlAfterApplyVerification() {"))
        assertTrue(source.contains("logActivity(\"Refresh UML From Code rereads the changed code from disk after apply.\")"))
        assertTrue(source.contains("status(\"Verifying applied changes in UML\")"))
        assertTrue(source.contains("generateProjectUml()"))
    }
}
