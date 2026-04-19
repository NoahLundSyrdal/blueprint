package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RefreshSkippedPathsInlineSummaryRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `refresh success appends skipped path scope summary after apply`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun skippedPathInlineSummary("))
        assertTrue(source.contains("prefix: String,"))
        assertTrue(source.contains("topReasonLimit: Int = 2"))
        assertTrue(source.contains("examplePathLimit: Int = 2"))
        assertTrue(source.contains("return \"\$prefix \${context.skippedFiles.size} Python path\${if (context.skippedFiles.size == 1) \" was\" else \"s were\"} skipped during Refresh UML From Code (\$topReasons). Blueprint still built the current code-backed UML from the Python files it could read, so inspect skipped paths like \$examplePaths if anything looks incomplete. Fix the folder or files if needed, then Refresh UML From Code again before Generate Code Diff.\""))
        assertTrue(source.contains("skippedPathInlineSummary(context, prefix = \"Refresh scope:\")"))
        assertTrue(source.contains("if (refreshedAfterApply) {"))
        assertTrue(source.contains("postApplyVerifyState = listOfNotNull(postApplyVerifyState, summary).joinToString(\" \")"))
    }

    @Test
    fun `empty state reuses skipped path helper for concise scope note`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun emptyStatePartialRefreshNote(context: PythonProjectAnalyzer.PythonProjectContext): String ="))
        assertTrue(source.contains("skippedPathInlineSummary(context, prefix = \"Partial refresh note:\")"))
    }
}
