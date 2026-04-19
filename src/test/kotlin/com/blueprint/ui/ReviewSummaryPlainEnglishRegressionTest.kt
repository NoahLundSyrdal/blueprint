package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReviewSummaryPlainEnglishRegressionTest {
    private val source = File("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt").readText()

    @Test
    fun `review summary prompts plain English overview before raw diff`() {
        assertTrue(
            source.contains(
                "What changed? Review the plain-English summary below, then open Preview Diff to inspect the raw diff before apply."
            )
        )
        assertFalse(source.contains("Next: open Preview Diff to inspect the raw diff before apply."))
    }
}
