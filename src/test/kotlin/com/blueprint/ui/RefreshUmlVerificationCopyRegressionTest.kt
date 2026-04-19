package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RefreshUmlVerificationCopyRegressionTest {
    @Test
    fun `post apply verification copy uses refresh uml from code wording consistently`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("toolTipText = \"After apply, Blueprint already refreshed the code-backed UML once. Use Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself.\""))
        assertTrue(source.contains("appendLine(\"Refresh UML From Code verification:\")"))
        assertTrue(source.contains("Click Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself."))
        assertTrue(source.contains("Next: Refresh UML From Code to verify the updated code-backed UML."))
    }
}
