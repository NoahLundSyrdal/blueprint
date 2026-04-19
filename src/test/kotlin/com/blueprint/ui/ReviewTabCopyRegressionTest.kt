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

        assertTrue(source.contains("reviewSummaryArea.text = PatchChangeSummary.reviewSummary(exec)"))
        assertTrue(source.contains("reviewSummaryArea.text = PatchChangeSummary.reviewSummary(changedExec)"))
        assertTrue(source.contains("Plain-English summary before apply:"))
        assertTrue(source.contains("ReviewExplanation.details("))
        assertTrue(source.contains("reviewDetails.joinToString(\"\\n\")"))
        assertTrue(source.contains("\"Why is it safe to apply?\""))
        assertTrue(source.contains("\"Why is it blocked?\""))
    }
}
