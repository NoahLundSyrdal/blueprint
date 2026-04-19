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
        assertTrue(source.contains("No code-backed UML is loaded yet. Start with Refresh UML From Code to read the current project into an editable UML diagram."))
        assertTrue(source.contains("Refresh UML From Code scans Python files for the code-backed UML and may skip non-Python folders, generated artifacts, and files it cannot parse yet."))
        assertTrue(source.contains("Blueprint found Python files, but no classes were extracted into the code-backed UML yet."))
        assertTrue(source.contains("This folder does not look like a supported Python project yet."))
        assertTrue(source.contains("Blueprint could not find Python files to turn into a code-backed UML diagram."))
    }

    @Test
    fun `unsupported python guidance uses analyzer notes for next steps`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val nextStep = context.notes.firstOrNull()"))
        assertTrue(source.contains("?: \"Open a Python folder or add .py files, then click Refresh UML From Code again.\""))
        assertTrue(source.contains("Next steps: review the inferred source roots, open a Python file to confirm the folder you want, or keep editing the project and refresh again."))
        assertTrue(source.contains("Next steps: open a Python source root, add .py files, or open source files manually while you pick the folder to map."))
        assertTrue(source.contains("joinToString(\" \")"))
    }
}
