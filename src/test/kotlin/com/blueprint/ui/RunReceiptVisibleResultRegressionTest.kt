package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RunReceiptVisibleResultRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `manual demo runner dialog and activity log emphasize seeing the feature exist`() {
        assertTrue(source.contains("6. Run the changed app with: \$runCommand"))
        assertTrue(source.contains("7. Confirm the expected visible result in the changed app."))
        assertTrue(source.contains("Blueprint records the run command and the visible result in Activity so the full demo path reads like a receipt."))
        assertTrue(source.contains("Demo e2e step passed: Run the changed app with \$runCommand. Confirmed visible result: \$expectedVisibleResult"))
        assertTrue(source.contains("status(\"Demo run and visible result recorded\")"))
    }
}
