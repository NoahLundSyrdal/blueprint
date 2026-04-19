package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ActivityReceiptStepCopyRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `activity receipt labels the main demo path as ordered steps`() {
        assertTrue(source.contains("Step 1 complete - Scanned project and generated UML:"))
        assertTrue(source.contains("Step 2 started - Preparing reviewed code patch for"))
        assertTrue(source.contains("Step 2 progress - Reviewed code patch plan is ready for"))
        assertTrue(source.contains("Step 2 complete - Reviewed code patch is ready for"))
        assertTrue(source.contains("Step 3 complete - \$msg"))
        assertTrue(source.contains("Step 4 complete - Applied approved changes for"))
        assertTrue(source.contains("Step 5 complete - Refreshed UML from code after apply:"))
        assertTrue(source.contains("Step 6 complete - "))
        assertTrue(source.contains("Optional inspection - \$msg"))
        assertTrue(source.contains("Optional run check - Opened likely entry file for manual verification:"))
    }
}
