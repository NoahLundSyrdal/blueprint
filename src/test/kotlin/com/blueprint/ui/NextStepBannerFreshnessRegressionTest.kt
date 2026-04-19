package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class NextStepBannerFreshnessRegressionTest {
    @Test
    fun `next-step banner keeps detail text separate from explicit stale banner`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("private fun updateNextStepBanner(title: String, detail: String) {"))
        assertTrue(source.contains("nextStepDetailLabel.text = detail"))
        assertTrue(source.contains("updateStaleDiffBanner()"))
        assertFalse(source.contains("private fun nextStepFreshnessHint(): String"))
        assertFalse(source.contains("val freshnessHint = nextStepFreshnessHint()"))
        assertFalse(source.contains("nextStepDetailLabel.text = listOf(detail, freshnessHint).filter { it.isNotBlank() }.joinToString(\" \")"))
        assertTrue(source.contains("bannerHint = \"No reviewed patch yet.\""))
        assertTrue(source.contains("bannerHint = \"Reviewed patch is stale.\""))
        assertTrue(source.contains("bannerHint = \"Reviewed patch is fresh.\""))
        assertTrue(source.contains("\"Stale reviewed code patch.\""))
    }
}
