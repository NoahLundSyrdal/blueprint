package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class GenerateDiffGuideSummaryRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `generate code diff guidance previews what will run in plain English`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun generateDiffGuideSummary("))
        assertTrue(source.contains("val lines = mutableListOf(\"Change the UML with chat or direct edits, then Generate Code Diff.\")"))
        assertTrue(source.contains("Generate Code Diff will create a reviewed code patch for your current UML edits."))
        assertTrue(source.contains("lines += validationCommandReviewText(validationCommand)"))
        assertTrue(source.contains("lines += runCommandReviewText(context)"))
        assertTrue(source.contains("val commandSummary = preApplyCommandSummary(context, validationCommand)"))
        assertTrue(source.contains("guideLabel.text = listOf(diffGuide.guideText, diffGuide.commandSummary)"))
        assertTrue(source.contains("updateNextStepBanner(\"Next: Generate Code Diff\", diffGuide.nextStepDetail)"))
        assertTrue(source.contains("Create a reviewed code patch for the current UML edits."))
        assertTrue(source.contains("Turn the current UML edits into a reviewed code patch before apply."))
    }
}
