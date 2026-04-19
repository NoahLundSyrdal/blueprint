package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyReceiptClarificationRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `post apply receipt distinguishes automatic refresh from explicit verify`() {
        assertTrue(source.contains("private const val POST_APPLY_REFRESH_NOTE = \"Blueprint already refreshed the code-backed UML automatically after apply. Use Refresh UML From Code to verify the updated code-backed UML again whenever you want to confirm it yourself.\""))
        assertTrue(source.contains("private const val POST_APPLY_VERIFY_RELOADED_LINE = \"- Blueprint already reloaded the changed code into the UML automatically after apply.\""))
        assertTrue(source.contains("appendLine(\"- Click \$POST_APPLY_VERIFY_PROMPT\")"))
        assertTrue(source.contains("val verificationSummaryLine = if (highlightLine == \"Blueprint refreshed the code-backed UML after apply.\") {"))
    }
}
