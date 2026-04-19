package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ManualDemoRunnerVerifyStepRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `manual demo runner makes the post apply refresh step explicit verification`() {
        assertTrue(source.contains("5. Refresh UML From Code to verify the updated code-backed UML"))
        assertFalse(source.contains("5. Refresh UML From Code\\n6. Run the changed app with: \$runCommand"))
    }
}
