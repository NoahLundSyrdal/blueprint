package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class EmptyStateGuidanceRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `empty uml guide distinguishes supported python projects from sparse folders`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("guideLabel.text = emptyUmlGuideText()"))
        assertTrue(source.contains("private fun emptyUmlGuideText(): String"))
        assertTrue(source.contains("if (context.isPythonLikely()) {"))
        assertTrue(source.contains("No code-backed UML is loaded yet. Click Refresh UML From Code to read the current project into an editable UML diagram."))
        assertTrue(source.contains("This folder does not look like a supported Python project yet."))
    }

    @Test
    fun `unsupported python guidance uses analyzer notes for next steps`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val nextStep = context.notes.firstOrNull()"))
        assertTrue(source.contains("?: \"Open a Python folder or add .py files, then click Refresh UML From Code again.\""))
        assertTrue(source.contains("return \"This folder does not look like a supported Python project yet. \$nextStep\""))
    }
}
