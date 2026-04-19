package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RunVerificationSuccessBannerRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `demo run verification shows end to end success banner`() {
        assertTrue(source.contains("private fun showRunVerificationSuccessBanner("))
        assertTrue(source.contains("showRunVerificationSuccessBanner(runCommand, expectedVisibleResult, true)"))
        assertTrue(source.contains("End-to-end success"))
        assertTrue(source.contains("Verify now:"))
        assertTrue(source.contains("Summary"))
        assertTrue(source.contains("Proof recorded:"))
        assertTrue(source.contains("Blueprint completed the full demo path: code-backed UML -> refined UML -> reviewed code patch -> applied changes -> refreshed UML -> running app."))
        assertTrue(source.contains("- Run verified with: \$runCommand"))
        assertTrue(source.contains("Rerun ready: use Run In Blueprint to rerun \$runCommand after your next approved change"))
        assertTrue(source.contains("- Visible result: \$visibleResult"))
        assertTrue(source.contains("Messages.showInfoMessage(project, banner, \"Blueprint - End-to-End Success\")"))
        assertTrue(source.contains("appendChat(\"Blueprint\", banner)"))
    }
}
