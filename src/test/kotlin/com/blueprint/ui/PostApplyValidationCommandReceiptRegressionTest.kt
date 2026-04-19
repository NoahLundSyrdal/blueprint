package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyValidationCommandReceiptRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `post apply receipt includes exact validation command when validation runs`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val receiptValidationCommandLine = validationCommandReceiptLine(result)"))
        assertTrue(source.contains("receiptValidationCommandLine?.let { appendLine(\"- \$it\") }"))
        assertTrue(source.contains("private fun validationCommandReceiptLine(result: ProjectValidationService.ValidationResult): String? ="))
        assertTrue(source.contains("?.let { \"Validation command: \$it\" }"))
    }

    @Test
    fun `skipped validation without inferred command does not invent a command line`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("result.command.takeIf { it.isNotBlank() && result.status != ProjectValidationService.ValidationResult.Status.SKIPPED }"))
        assertFalse(source.contains("Validation command: not available"))
    }
}
