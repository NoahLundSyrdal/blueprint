package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyReceiptClarificationRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `post apply receipt distinguishes automatic refresh from explicit verify`() {
        assertTrue(source.contains("val refreshNote = \"Blueprint already refreshed the code-backed UML from disk after apply. Refresh UML From Code to verify again whenever you want to rerun that refresh.\""))
        assertTrue(source.contains("val umlRefreshLine = \"Blueprint automatically refreshed the code-backed UML from disk after apply.\""))
        assertTrue(source.contains("appendLine(\"- Blueprint already reloaded the changed code into the UML after apply.\")"))
        assertTrue(source.contains("Click Refresh UML From Code when you want to verify that reload yourself."))
        assertTrue(source.contains("Blueprint already refreshed the code-backed UML from disk after apply. Refresh UML From Code to verify again whenever you want to rerun that refresh."))
    }
}
