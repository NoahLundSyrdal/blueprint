package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewTabFreshnessRegressionTest {
    @Test
    fun `review tab freshness copy stays discoverable in ui source`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("private data class ReviewFreshnessState("))
        assertTrue(source.contains("artifactLabel.text = \"Artifacts: plan=") && source.contains("| diff=") && source.contains("reviewFreshness.badge"))
        assertTrue(source.contains("appendLine(\"- Diff status: \${freshness.badge}\")"))
        assertTrue(source.contains("private val staleDiffBannerLabel = JLabel().apply {"))
        assertTrue(source.contains("add(staleDiffBannerLabel.apply {"))
        assertTrue(source.contains("private fun updateStaleDiffBanner() {"))
        assertTrue(source.contains("val bannerText = freshness?.takeIf { it.badge == \"STALE\" }?.let {"))
        assertTrue(source.contains("\"Stale reviewed code patch.\""))
        assertTrue(source.contains("staleDiffBannerLabel.isVisible = bannerText.isNotBlank()"))
        assertTrue(source.contains("This reviewed code patch is stale because the UML changed after review. Generate Code Diff again before Apply Approved Changes."))
        assertTrue(source.contains("This reviewed code patch matches the current UML. Apply Approved Changes, or keep editing and then Generate Code Diff again."))
        assertTrue(source.contains("This reviewed code patch matches the refreshed code-backed UML. Refresh UML From Code again anytime to verify after more edits."))
        assertTrue(source.contains("lastReviewedUmlByNodeId[n.id] = normalizedUmlText()"))
        assertTrue(source.contains("refreshedAfterApply = true"))
    }
}
