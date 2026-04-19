package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RefreshUmlVerificationCopyRegressionTest {
    @Test
    fun `post apply verification copy uses refresh uml from code wording consistently`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("toolTipText = \"After apply, Blueprint refreshes UML automatically. Use Refresh UML From Code to rerun that refresh yourself and verify the changed files again.\""))
        assertTrue(source.contains("appendLine(\"Refresh UML From Code verification:\")"))
        assertTrue(source.contains("Click Refresh UML From Code when you want to verify that reload yourself."))
        assertTrue(source.contains("- Refresh UML From Code to verify the updated code-backed UML."))
    }
}
