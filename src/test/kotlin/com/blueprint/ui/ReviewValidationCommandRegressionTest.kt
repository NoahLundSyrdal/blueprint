package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewValidationCommandRegressionTest {
    @Test
    fun `review explanation requests the inferred validation command before apply`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("validationCommand = project.service<ProjectValidationService>().selectedCommand()"))
        assertTrue(source.contains("val validationCommandLine = validationCommand?.let { \"Validation after apply: \$it\" }"))
        assertTrue(source.contains("Validation after apply: Blueprint could not infer a validation command, so validation will be skipped unless you run checks manually."))
    }
}
