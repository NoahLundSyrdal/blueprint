package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewChangedFilesHeaderRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `review summary surfaces changed files count in the header`() {
        assertTrue(source.contains("val changedFilesHeader = reviewChangedFilesHeader(exec)"))
        assertTrue(source.contains("appendLine(changedFilesHeader)"))
        assertTrue(source.contains("private fun reviewChangedFilesHeader(exec: ExecutionArtifact?): String {"))
        assertTrue(source.contains("private fun reviewScopeSummaryLine(exec: ExecutionArtifact?): String {"))
        assertTrue(source.contains("appendLine(scopeSummaryLine)"))
        assertTrue(source.contains("0 -> \"Changed files: 0 files\""))
        assertTrue(source.contains("1 -> \"Changed files: 1 file\""))
        assertTrue(source.contains("else -> \"Changed files: \$count files\""))
        assertTrue(source.contains("\"Diff scope summary: no files will change before apply.\""))
        assertTrue(source.contains("\"Diff scope summary: only \${paths.first()} will change before apply.\""))
        assertTrue(source.contains("\"Diff scope summary: \${paths.size} files will change before apply (\${paths.joinToString(\", \")}).\""))
    }
}
