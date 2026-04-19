package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyVerifiedReceiptRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `post apply receipt ties validation changed files and verification into one checklist`() {
        assertTrue(source.contains("Verified receipt:"))
        assertTrue(source.contains("- Refresh UML From Code to verify the updated code-backed UML."))
        assertTrue(source.contains("- Run the changed app to confirm the feature exists."))
        assertTrue(source.contains("- Review the changed paths, validation result, and inferred run command above."))
        assertTrue(source.contains("- Open Changed Files remains available if you want to inspect what Blueprint wrote after verification."))
    }
}
