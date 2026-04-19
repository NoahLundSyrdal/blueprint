package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class EmptyStateGuidanceRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `empty uml guide distinguishes supported python projects from sparse folders`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("guideLabel.text = emptyUmlGuideText()"))
        assertTrue(source.contains("private fun emptyUmlGuideText(): String"))
        assertTrue(source.contains("if (context.isPythonLikely()) {"))
        assertTrue(source.contains("Start by reading the current project into an editable UML diagram."))
        assertTrue(source.contains("Blueprint has not found enough Python project structure yet. Open a Python folder or add .py files, then click Refresh UML From Code again."))
    }
}
