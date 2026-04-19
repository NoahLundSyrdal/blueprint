package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ActivityRunPhaseReceiptRegressionTest {
    private val source = File("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt").readText()

    @Test
    fun `activity receipt distinguishes reset prompt and run phases`() {
        assertTrue(source.contains("Step 0 reset complete - "))
        assertTrue(source.contains("Step 1 demo prompt ready - "))
        assertTrue(source.contains("Step 6 run started - Checklist command:"))
        assertTrue(source.contains("Step 6 run started - In-app run:"))
        assertTrue(source.contains("Step 6 run finished - In-app run finished:"))
        assertTrue(source.contains("Step 6 run stopped - In-app run stopped:"))
        assertTrue(source.contains("Step 6 blocked - In-app run failed:"))
    }
}
