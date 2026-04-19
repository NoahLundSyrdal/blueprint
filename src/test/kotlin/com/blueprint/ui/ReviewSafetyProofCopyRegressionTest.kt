package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReviewSafetyProofCopyRegressionTest {
    private val source = File("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt").readText()

    @Test
    fun `approved review copy says apply is safe now`() {
        assertTrue(source.contains("That is why Apply Approved Changes is safe now."))
        assertTrue(source.contains("- Why it is safe: review found no blocking scope or safety issues, so Apply Approved Changes is safe now."))
    }
}
