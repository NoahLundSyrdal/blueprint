package com.blueprint.service

import org.junit.Assert.assertTrue
import org.junit.Test

class PythonProjectAnalyzerContextSummaryTest {
    @Test
    fun `summary checklist handles missing commands cleanly`() {
        val prompt = PythonProjectAnalyzer.PythonProjectContext(
            basePath = "/tmp/project",
            configFiles = emptyList(),
            sourceRoots = emptyList(),
            testRoots = emptyList(),
            packageManager = "unknown",
            frameworks = emptyList(),
            testCommands = emptyList(),
            runCommands = emptyList(),
            notes = emptyList(),
        ).promptContext()

        assertTrue(prompt.contains("Python quick checklist"))
        assertTrue(prompt.contains("- Source roots: No Python source roots detected yet."))
        assertTrue(prompt.contains("- Validation command: No validation command inferred yet."))
        assertTrue(prompt.contains("- Run command: No run command inferred yet."))
        assertTrue(prompt.contains("PYTHON_PROJECT_CONTEXT"))
    }
}
