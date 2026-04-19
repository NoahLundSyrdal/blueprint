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
        assertTrue(source.contains("toolTipText = \"After apply, Blueprint already refreshed the code-backed UML once. Use Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself.\""))
        assertTrue(source.contains("addActionListener { refreshUmlAfterApplyVerification() }"))
        assertTrue(source.contains("add(verifyInUmlButton)"))
        assertTrue(source.contains("verifyInUmlButton.isEnabled = n.executionStatus == ExecutionStatus.APPLIED"))
        assertTrue(source.contains("verifyInUmlButton.isEnabled = true"))
        assertTrue(source.contains("\${postApplyVerifyState} Refresh UML From Code verifies the updated code-backed UML again whenever you want to confirm it yourself."))
        assertTrue(source.contains("Blueprint automatically refreshed the code-backed UML from disk after apply."))
        assertTrue(source.contains("appendLine(\"- Blueprint already reloaded the changed code into the UML automatically after apply.\")"))
        assertTrue(source.contains("Click Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself."))
        assertTrue(source.contains("private fun refreshUmlAfterApplyVerification() {"))
        assertTrue(source.contains("postApplyVerifyState = \"Blueprint reran Refresh UML From Code after apply so you can verify the latest code-backed UML yourself.\""))
        assertTrue(source.contains("logActivity(\"Refresh UML From Code reran after apply so you can verify the changed code from disk yourself.\")"))
        assertTrue(source.contains("status(\"Rerunning Refresh UML From Code after apply\")"))
        assertTrue(source.contains("generateProjectUml()"))
    }
}
