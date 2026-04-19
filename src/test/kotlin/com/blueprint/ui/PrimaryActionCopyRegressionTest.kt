package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PrimaryActionCopyRegressionTest {
    @Test
    fun `primary action defaults to refresh when no uml is loaded`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("primaryActionButton.text = \"Refresh UML From Code\""))
        assertTrue(source.contains("guideLabel.text = emptyUmlGuideText()"))
        assertTrue(source.contains("updateNextStepBanner(\"Next: Refresh UML From Code\", \"Load the current Python project into a code-backed UML diagram before editing.\")"))
        assertTrue(source.contains("\"No code-backed UML is loaded yet. Click Refresh UML From Code to read the current project into an editable UML diagram.\""))
    }

    @Test
    fun `primary action switches to apply when approved patch is selected`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("primaryActionButton.text = \"Apply Approved Changes\""))
        assertTrue(source.contains("guideLabel.text = \"Review approved the reviewed code patch because it stays in scope and has no blocking safety issues. Apply Approved Changes to write it to disk, then Blueprint will validate the project.\""))
        assertTrue(source.contains("updateNextStepBanner(\"Next: Apply Approved Changes\", \"Review approved the current patch because it stays in scope and has no blocking safety issues, so this is the safe time to write it to disk.\")"))
        assertTrue(source.contains("val reviewDetail = review?.let { ReviewExplanation.statusLine(shortTitle, registry.getExecution(selected.id), it) }"))
        assertTrue(source.contains("?: \"Review not run yet. Generate Code Diff first so Blueprint can explain why the patch is safe to apply.\""))
        assertTrue(source.contains("\"Next: Review approved\" to reviewDetail"))
    }

    @Test
    fun `primary action switches to generate diff when uml edits are ready`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("primaryActionButton.text = \"Generate Code Diff\""))
        assertTrue(source.contains("val diffGuide = generateDiffGuideSummary()"))
        assertTrue(source.contains("guideLabel.text = listOf(diffGuide.guideText, diffGuide.commandSummary)"))
        assertTrue(source.contains("updateNextStepBanner(\"Next: Generate Code Diff\", diffGuide.nextStepDetail)"))
    }

    @Test
    fun `next step banner includes failed validation guidance`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("updateNextStepBanner(\"Next: Generate Code Diff\", \"Validation failed after apply, so the reviewed code patch needs another pass.\")"))
    }
}
