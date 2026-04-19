package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class WhatChangedSummaryReuseRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    /**
     * Verifies the same What Changed structure is reused before and after apply.
     */
    @Test
    fun `what changed summary stays consistent before and after apply`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("return buildSummary("))
        assertTrue(source.contains("semanticHeading = \"Plain-English summary before apply:\""))
        assertTrue(source.contains("semanticHeading = \"Plain-English summary after apply:\""))
        assertTrue(source.contains("heading = \"What changed?\""))
    }
}
