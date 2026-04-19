package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ManualDemoRunnerChangedFilesRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `manual demo runner mentions changed files to inspect in the ide`() {
        assertTrue(source.contains("val changedPaths = postApplyInlineSummary?.changedPaths.orEmpty().distinct()"))
        assertTrue(source.contains("Changed files to inspect in the IDE:"))
        assertTrue(source.contains("Changed file to inspect in the IDE:"))
        assertTrue(source.contains("6. Inspect the changed file(s) in the IDE"))
        assertTrue(source.contains("Inspect this file after Apply Approved Changes, then confirm the visible result."))
        assertTrue(source.contains("Inspect these files after Apply Approved Changes, then confirm the visible result."))
    }
}
