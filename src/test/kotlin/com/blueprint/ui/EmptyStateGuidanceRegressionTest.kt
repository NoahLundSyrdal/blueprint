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
        assertTrue(source.contains("val folderSummary = emptyStateFolderSummary(context)"))
        assertTrue(source.contains("private fun emptyStateFolderSummary(context: PythonProjectAnalyzer.PythonProjectContext): String"))
        assertTrue(source.contains("Current folder: \$folderLabel. \$pythonScope"))
        assertTrue(source.contains("Refresh UML From Code scans Python files for the code-backed UML and may skip non-Python folders, generated artifacts, and files it cannot parse yet."))
        assertTrue(source.contains("val partialRefreshNote = emptyStatePartialRefreshNote(context)"))
        assertTrue(source.contains("private fun emptyStatePartialRefreshNote(context: PythonProjectAnalyzer.PythonProjectContext): String ="))
        assertTrue(source.contains("private fun skippedPathInlineSummary("))
        assertTrue(source.contains("skippedPathInlineSummary(context, prefix = \"Partial refresh note:\")"))
        assertTrue(source.contains("return \"\$prefix \${context.skippedFiles.size} Python path\${if (context.skippedFiles.size == 1) \" was\" else \"s were\"} skipped during Refresh UML From Code (\$topReasons)."))
        assertTrue(source.contains("Blueprint still built the current code-backed UML from the Python files it could read, so inspect skipped paths like \$examplePaths if anything looks incomplete. Fix the folder or files if needed, then Refresh UML From Code again before Generate Code Diff."))
        assertTrue(source.contains("Blueprint found Python files, but no classes were extracted into the code-backed UML yet."))
        assertTrue(source.contains("No Python files were found in the opened folder, so Blueprint cannot build a code-backed UML diagram yet."))
        assertTrue(source.contains("Open the Python app folder or a Python subfolder you want to map, then click Refresh UML From Code again."))
        assertTrue(source.contains("If you are still choosing the folder, you can open source files manually or paste/import UML first and come back to code refresh later."))
    }

    @Test
    fun `unsupported python guidance uses analyzer notes for next steps`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val nextStep = context.notes.firstOrNull()"))
        assertTrue(source.contains("?: \"Open a Python folder or add .py files, then click Refresh UML From Code again.\""))
        assertTrue(source.contains("Next steps: review the inferred source roots, open a Python file to confirm the folder you want, or keep editing the project and refresh again."))
        assertTrue(source.contains("Next steps: open a Python source root, open a Python subfolder, add .py files, or paste/import UML while you pick the folder to map."))
        assertTrue(source.contains("joinToString(\" \")"))
    }
}
