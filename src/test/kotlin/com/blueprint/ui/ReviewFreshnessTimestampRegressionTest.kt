package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewFreshnessTimestampRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `review freshness includes reviewed at line and age text in review area and artifact summary`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private val reviewedAtByNodeId = mutableMapOf<String, Instant>()"))
        assertTrue(source.contains("reviewedAtByNodeId[n.id] = Instant.now()"))
        assertTrue(source.contains("val reviewedAt = reviewedAtByNodeId[node.id]"))
        assertTrue(source.contains("val reviewedAtLine = reviewedAtLine(reviewedAt)"))
        assertTrue(source.contains("appendLine(\"- \${freshness.reviewedAtLine}\")"))
        assertTrue(source.contains("reviewFreshness.reviewedAtLine.lowercase(Locale.US)"))
        assertTrue(source.contains("Reviewed at not available yet"))
        assertTrue(source.contains("Reviewed at "))
        assertTrue(source.contains("reviewed just now"))
        assertTrue(source.contains("reviewed \$minutes min ago"))
        assertTrue(source.contains("reviewed \$hours hr ago"))
        assertTrue(source.contains("reviewed \$days day"))
    }
}
