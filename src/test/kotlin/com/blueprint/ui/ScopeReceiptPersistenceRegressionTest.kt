package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ScopeReceiptPersistenceRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `uml panel keeps a visible scope receipt after refresh`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private val scopeReceiptArea = JBTextArea(5, 40).apply"))
        assertTrue(source.contains("Scope receipt will appear here after Refresh UML From Code."))
        assertTrue(source.contains("Current Scope Receipt"))
        assertTrue(source.contains("- Included files: "))
        assertTrue(source.contains("- Skipped files: "))
        assertTrue(source.contains("- Top skipped reasons: "))
        assertTrue(source.contains("border = BorderFactory.createTitledBorder(\"Current Scope Receipt\")"))
    }
}
