package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ValidationCommandReceiptRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `post apply validation receipts show the exact command when available`() {
        assertTrue(source.contains("\"Applied changes. Running validation:\\n\$command\""))
        assertTrue(source.contains("status(if (command == null) \"Validation skipped: no command inferred\" else \"Running validation: \$command\")"))
        assertTrue(source.contains("appendLine(\"\${result.detailLabel()} command: \${result.command.ifBlank { \"not available\" }}\")"))
    }

    @Test
    fun `validation failure copy points users back to generate code diff or manual inspection`() {
        assertTrue(source.contains("Validation failed after apply. Generate Code Diff again after you fix the problem, or inspect the related file manually before continuing."))
    }
}
