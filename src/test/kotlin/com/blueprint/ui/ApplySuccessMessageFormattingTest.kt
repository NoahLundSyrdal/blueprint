package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ApplySuccessMessageFormattingTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `apply completion dialog uses appendLine for validation block`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("appendLine(\"Changed paths:\")"))
        assertTrue(source.contains("changedPaths.forEach { appendLine(\"- \$it\") }"))
        assertTrue(source.contains("appendLine(\"Validation:\")"))
        assertTrue(source.contains("appendLine(validationReportText(result))"))
        assertFalse(source.contains("append(validationReportText(result))"))
    }
}
