package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyOpenFilesActionRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `apply dialog points to open changed files as an optional inspection step`() {
        assertTrue(source.contains("Optional after verification: Use Open Changed File if you want to inspect exactly what Blueprint wrote."))
        assertTrue(source.contains("Optional after verification: Use Open Changed Files if you want to inspect exactly what Blueprint wrote."))
    }
}
