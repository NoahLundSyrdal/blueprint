package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyVerifyInCodeRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `apply success copy offers explicit verify in code follow-up`() {
        assertTrue(source.contains("Verify in code: Use Open Changed File to inspect the primary changed file in the IDE. This does not apply or refresh anything."))
        assertTrue(source.contains("Verify in code: Use Open Changed Files to inspect the primary changed files in the IDE. This does not apply or refresh anything."))
        assertTrue(source.contains("Verify in code: Use Open Changed File to inspect "))
        assertTrue(source.contains("Verify in code: Use Open Changed Files to inspect the "))
    }
}
