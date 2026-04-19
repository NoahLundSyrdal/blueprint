package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewInlineChangedPathsRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `review summary keeps changed file count and changed paths together near the header`() {
        assertTrue(source.contains("val changedFilesInline = reviewChangedFilesInline(exec)"))
        assertTrue(source.contains("appendLine(changedFilesInline)"))
        assertTrue(source.contains("private fun reviewChangedFilesInline(exec: ExecutionArtifact?): String {"))
        assertTrue(source.contains("0 -> \"Changed paths: none\""))
        assertTrue(source.contains("1 -> \"Changed path: \${paths.first()}\""))
        assertTrue(source.contains("else -> \"Changed paths: \${paths.joinToString(\", \")}\""))
    }
}
