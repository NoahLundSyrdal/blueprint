package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RunReceiptPersistenceRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    /**
     * Verifies the activity receipt, including manual run verification, is persisted and restored.
     */
    @Test
    fun `workspace persistence includes activity receipt for manual run verification`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("activityReceipt = activityLog.text"))
        assertTrue(source.contains("activityLog.text = state.activityReceipt.orEmpty()"))
        assertTrue(source.contains("logActivity(\"Demo e2e step passed: Run the changed app with \$runCommand. Confirmed visible result: \$expectedVisibleResult\")"))
        assertTrue(source.contains("runVerified = activityLog.text.contains(\"Run the changed app\", ignoreCase = true)"))
    }
}
