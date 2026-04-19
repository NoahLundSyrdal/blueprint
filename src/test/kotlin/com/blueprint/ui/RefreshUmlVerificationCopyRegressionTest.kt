package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RefreshUmlVerificationCopyRegressionTest {
    @Test
    fun `post apply verification copy uses refresh uml from code wording consistently`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("toolTipText = \"Rerun Refresh UML From Code after apply so you can verify the changed files in the code-backed UML again.\""))
        assertTrue(source.contains("appendLine(\"Refresh UML From Code verification:\")"))
        assertTrue(source.contains("Click Refresh UML From Code when you want to verify that reload yourself."))
        assertTrue(source.contains("Refresh UML From Code lets you rerun the UML reload when you want an explicit verification step."))
    }
}
