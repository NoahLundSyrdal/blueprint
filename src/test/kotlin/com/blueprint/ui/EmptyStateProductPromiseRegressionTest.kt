package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class EmptyStateProductPromiseRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `python empty state explains the full blueprint loop`() {
        assertTrue(source.contains("Blueprint can open any Python folder, draw a code-backed UML diagram, help you refine it with chat or direct edits, generate a reviewed code diff, apply approved changes, verify in UML, and run the changed app."))
        assertTrue(source.contains("Start with Refresh UML From Code to read the current project into an editable UML diagram."))
        assertTrue(source.contains("After that, refine the UML, Generate Code Diff, Apply Approved Changes, Verify In UML, and Run In Blueprint."))
    }

    @Test
    fun `non python empty state explains the full blueprint loop once python files exist`() {
        assertTrue(source.contains("When this folder has Python files, Blueprint can turn them into a code-backed UML diagram, help you refine that UML, generate a reviewed code diff, apply approved changes, verify in UML, and run the changed app."))
        assertTrue(source.contains("Open a Python source root or add .py files, then Refresh UML From Code to start the full Blueprint loop."))
    }
}
