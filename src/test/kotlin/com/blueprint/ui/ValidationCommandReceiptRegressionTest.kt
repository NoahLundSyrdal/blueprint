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
    fun `validation failure copy points users to the safest recovery step`() {
        assertTrue(source.contains("private fun validationFailureRecoveryMessage(reviewFreshnessBadge: String?): String = when (reviewFreshnessBadge) {"))
        assertTrue(source.contains("\"Validation failed after apply. The reviewed code patch is stale now, so Generate Code Diff again after you adjust the UML or code. Inspect the related file manually first if you need to understand the failure.\""))
        assertTrue(source.contains("\"Validation failed after apply. Inspect the related file manually first. If you change the UML or code, Generate Code Diff again before you continue.\""))
        assertTrue(source.contains("\"Validation failed after apply. Inspect the related file manually first. If the fix changes the UML or code, Generate Code Diff again before you continue.\""))
    }
}
