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
        assertTrue(source.contains("guideLabel.text = \"Start by reading the current project into an editable UML diagram.\""))
    }

    @Test
    fun `primary action switches to apply when approved patch is selected`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("primaryActionButton.text = \"Apply Approved Changes\""))
        assertTrue(source.contains("guideLabel.text = \"Review approved the reviewed code patch. Apply Approved Changes to write it to disk, then Blueprint will validate the project.\""))
    }

    @Test
    fun `primary action switches to generate diff when uml edits are ready`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("primaryActionButton.text = \"Generate Code Diff\""))
        assertTrue(source.contains("guideLabel.text = \"Change the UML with chat or direct edits, then Generate Code Diff.\""))
    }
}
