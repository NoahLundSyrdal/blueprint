package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PreApplyCommandSummaryRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `generate diff guidance includes a clear pre-apply command summary`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val commandSummary = preApplyCommandSummary(context, validationCommand)"))
        assertTrue(source.contains("val guideText = (lines + commandSummary.lines()).joinToString(\" \")"))
        assertTrue(source.contains("return GenerateDiffGuideSummary(guideText, nextStepDetail, commandSummary)"))
        assertTrue(source.contains("guideLabel.text = listOf(diffGuide.guideText, diffGuide.commandSummary)"))
        assertTrue(source.contains(".joinToString(\"\\n\")"))
    }

    @Test
    fun `pre-apply command summary explains inferred and unavailable commands`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun preApplyCommandSummary("))
        assertTrue(source.contains("\"Before apply, Blueprint expects:\""))
        assertTrue(source.contains("Validation after apply was inferred automatically:"))
        assertTrue(source.contains("Validation after apply is unavailable. Blueprint did not infer a validation command"))
        assertTrue(source.contains("Run after apply was inferred automatically:"))
        assertTrue(source.contains("Run after apply is unavailable."))
    }
}
