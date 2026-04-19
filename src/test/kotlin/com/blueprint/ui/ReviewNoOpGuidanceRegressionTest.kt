package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewNoOpGuidanceRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `review summary explains no-op diffs in the review surface`() {
        assertTrue(source.contains("Plain-English summary before apply:"))
        assertTrue(source.contains("Blueprint compared the current UML-backed request against the code on disk. Refresh UML From Code to verify the current code, or refine the UML and try a different change."))
        assertTrue(source.contains("Changed files: none."))
    }
}
