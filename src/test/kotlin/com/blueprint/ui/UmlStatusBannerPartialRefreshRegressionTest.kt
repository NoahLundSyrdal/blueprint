package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class UmlStatusBannerPartialRefreshRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `uml status banner distinguishes complete and partial refresh states`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val refreshStatus = if (context.skippedFiles.isEmpty())"))
        assertTrue(source.contains("UML: complete code-backed UML loaded."))
        assertTrue(source.contains("UML: partial code-backed UML loaded."))
        assertTrue(source.contains("Review the skipped-path guidance if anything looks incomplete."))
        assertTrue(source.contains("umlStatusLabel.text = refreshStatus"))
    }
}
