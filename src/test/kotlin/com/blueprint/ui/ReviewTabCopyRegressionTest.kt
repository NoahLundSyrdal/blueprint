package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewTabCopyRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `review tab keeps what changed separate from safety explanation`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("reviewSummaryArea.text = buildReviewSummary(exec, reviewFreshness)"))
        assertTrue(source.contains("reviewSummaryArea.text = PatchChangeSummary.reviewSummary(changedExec)"))
        assertTrue(source.contains("val scopeSentence = reviewScopeSentence(exec)"))
        assertTrue(source.contains("appendLine(scopeSentence)"))
        assertTrue(source.contains("Only 1 file will change:"))
        assertTrue(source.contains("No files will change in this reviewed code patch."))
        assertTrue(source.contains("files will change:"))
        assertTrue(source.contains("appendLine(\"Diff status: "))
        assertTrue(source.contains("Plain-English summary before apply:"))
        assertTrue(source.contains("ReviewExplanation.details("))
        assertTrue(source.contains("reviewDetails.joinToString(\"\\n\")"))
        assertTrue(source.contains("\"Why is it safe to apply?\""))
        assertTrue(source.contains("\"Why is it blocked?\""))
    }
}
