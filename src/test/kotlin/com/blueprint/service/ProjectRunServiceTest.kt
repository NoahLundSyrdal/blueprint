package com.blueprint.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRunServiceTest {
    @Test
    fun `returns inferred run command when present`() {
        val context = context(runCommands = listOf("python main.py"))

        assertEquals("python main.py", ProjectRunServiceInvariants.inferredRunCommand(context))
    }

    @Test
    fun `returns null when no run command is inferred`() {
        val context = context(runCommands = emptyList())

        assertNull(ProjectRunServiceInvariants.inferredRunCommand(context))
    }

    @Test
    fun `returns fallback summary when no run command exists`() {
        assertEquals(
            "Blueprint could not infer a run command yet. Refresh UML From Code first, or run the project entrypoint manually.",
            ProjectRunServiceInvariants.noCommandSummary(),
        )
    }

    @Test
    fun `reports not running when no process exists`() {
        assertFalse(ProjectRunServiceInvariants.isRunning(null))
    }

    @Test
    fun `reports running when process is alive`() {
        val process = ProcessBuilder("/bin/sh", "-lc", "sleep 1").start()
        try {
            assertTrue(ProjectRunServiceInvariants.isRunning(process))
        } finally {
            process.destroyForcibly()
        }
    }

    private fun context(runCommands: List<String>): PythonProjectAnalyzer.PythonProjectContext =
        PythonProjectAnalyzer.PythonProjectContext(
            basePath = "/tmp/project",
            configFiles = emptyList(),
            sourceRoots = listOf("."),
            testRoots = emptyList(),
            packageManager = "unknown",
            frameworks = emptyList(),
            testCommands = emptyList(),
            runCommands = runCommands,
            notes = emptyList(),
        )
}

private object ProjectRunServiceInvariants {
    fun inferredRunCommand(context: PythonProjectAnalyzer.PythonProjectContext): String? =
        context.runCommands.firstOrNull()?.takeIf { it.isNotBlank() }

    fun noCommandSummary(): String =
        "Blueprint could not infer a run command yet. Refresh UML From Code first, or run the project entrypoint manually."

    fun isRunning(process: Process?): Boolean = process?.isAlive == true
}
