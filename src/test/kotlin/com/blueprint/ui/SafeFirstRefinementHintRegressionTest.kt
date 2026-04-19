package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SafeFirstRefinementHintRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `first edit hint suggests safe reviewable uml changes before first diff`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("firstEditHint(context)?.let { lines += it }"))
        assertTrue(source.contains("private fun firstEditHint("))
        assertTrue(source.contains("if (umlHasPendingEdits || registry.all().isNotEmpty()) return null"))
        assertTrue(source.contains("return \"Safe first refinement: \${suggestions.joinToString(\" \")}\".trim()"))
        assertTrue(source.contains("private fun safeFirstRefinementSuggestions("))
        assertTrue(source.contains("Ask for one reviewable app-facing change such as 'add a status field to the main model' or 'rename one label shown by the entry flow'."))
        assertTrue(source.contains("Ask for one reviewable package change such as 'add a field to the primary CLI model' or 'link one helper class to the main package'."))
        assertTrue(source.contains("Ask for one reviewable UML change such as 'add a field to one class', 'rename one relationship', or 'extract one helper entity'."))
        assertTrue(source.contains("Keep the first edit scoped to one entity, one field, or one relationship before Generate Code Diff."))
    }
}
