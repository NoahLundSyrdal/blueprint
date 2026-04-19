package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewValidationCommandCopyRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `review summary highlights inferred validation command before apply`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("appendLine(\"Validation before apply:\")"))
        assertTrue(source.contains("appendLine(\"- \${validationCommandReviewText(validationCommand)}\")"))
        assertTrue(source.contains("buildReviewSummary(exec, review, reviewFreshness, validationCommand)"))
    }

    @Test
    fun `validation command copy keeps the no command fallback clear`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("?.let { \"Validation after apply: \$it\" }"))
        assertTrue(source.contains("?: \"Validation after apply is unavailable. Blueprint did not infer a validation command, so verify manually if you need extra checks.\""))
    }
}
