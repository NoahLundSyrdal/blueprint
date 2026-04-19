package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class NextStepBannerFreshnessRegressionTest {
    @Test
    fun `next-step banner includes freshness hints for missing stale and fresh reviewed patches`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("private fun nextStepFreshnessHint(): String"))
        assertTrue(source.contains("val freshnessHint = nextStepFreshnessHint()"))
        assertTrue(source.contains("nextStepDetailLabel.text = listOf(detail, freshnessHint).filter { it.isNotBlank() }.joinToString(\" \")"))
        assertTrue(source.contains("bannerHint = \"No reviewed patch yet.\""))
        assertTrue(source.contains("bannerHint = \"Reviewed patch is stale.\""))
        assertTrue(source.contains("bannerHint = \"Reviewed patch is fresh.\""))
        assertTrue(source.contains("Reviewed at not available yet."))
        assertTrue(source.contains("listOf(freshness.bannerHint, reviewedAtHint).filter { it.isNotBlank() }.joinToString(\" \")"))
    }
}
