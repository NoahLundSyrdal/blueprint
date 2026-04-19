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
    fun `returns fallback summary with detected entry candidates`() {
        val context = context(runCommands = emptyList(), runEntryCandidates = listOf("app.py", "src/server.py"))

        assertEquals(
            "Blueprint could not infer a run command yet. Refresh UML From Code first, then verify the feature manually. Open one of these likely entry files manually: app.py, src/server.py.",
            ProjectRunServiceInvariants.noCommandSummary(context),
        )
    }

    @Test
    fun `returns fallback summary with generic search list when no candidates exist`() {
        val context = context(runCommands = emptyList(), runEntryCandidates = emptyList())

        assertEquals(
            "Blueprint could not infer a run command yet. Refresh UML From Code first, then verify the feature manually. Blueprint looked for FastAPI, Flask, Streamlit, __main__.py, app.py, main.py, and __name__ == \"__main__\" entrypoints.",
            ProjectRunServiceInvariants.noCommandSummary(context),
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

    @Test
    fun `starting a run allows the real process to replace the launch guard`() {
        val guard = ProjectRunServiceInvariants.RunGuard()
        val launchId = guard.beginLaunch()

        assertTrue(launchId > 0)
        assertFalse(guard.isRunning())
        assertTrue(guard.attachProcess(launchId, ProcessBuilder("/bin/sh", "-lc", "sleep 1").start()).attached)
        try {
            assertTrue(guard.isRunning())
        } finally {
            guard.stopActiveProcess()
        }
    }

    @Test
    fun `rerun stops the previous active process before tracking the new one`() {
        val guard = ProjectRunServiceInvariants.RunGuard()
        val firstLaunch = guard.beginLaunch()
        val firstProcess = ProcessBuilder("/bin/sh", "-lc", "sleep 5").start()
        assertTrue(guard.attachProcess(firstLaunch, firstProcess).attached)

        val secondLaunch = guard.beginLaunch()
        val secondProcess = ProcessBuilder("/bin/sh", "-lc", "sleep 5").start()
        val attachment = guard.attachProcess(secondLaunch, secondProcess)

        try {
            assertTrue(attachment.stoppedPrevious)
            firstProcess.waitFor()
            assertFalse(firstProcess.isAlive)
            assertTrue(guard.isRunning())
        } finally {
            guard.stopActiveProcess()
        }
    }

    @Test
    fun `stale launch process is discarded when a newer run starts first`() {
        val guard = ProjectRunServiceInvariants.RunGuard()
        val staleLaunch = guard.beginLaunch()
        val freshLaunch = guard.beginLaunch()
        val freshProcess = ProcessBuilder("/bin/sh", "-lc", "sleep 5").start()
        assertTrue(guard.attachProcess(freshLaunch, freshProcess).attached)

        val staleProcess = ProcessBuilder("/bin/sh", "-lc", "sleep 5").start()
        val attachment = guard.attachProcess(staleLaunch, staleProcess)

        try {
            staleProcess.waitFor()
            assertFalse(attachment.attached)
            assertFalse(staleProcess.isAlive)
            assertTrue(guard.isRunning())
        } finally {
            guard.stopActiveProcess()
        }
    }

    private fun context(
        runCommands: List<String>,
        runEntryCandidates: List<String> = emptyList(),
    ): PythonProjectAnalyzer.PythonProjectContext =
        PythonProjectAnalyzer.PythonProjectContext(
            basePath = "/tmp/project",
            configFiles = emptyList(),
            sourceRoots = listOf("."),
            testRoots = emptyList(),
            packageManager = "unknown",
            frameworks = emptyList(),
            testCommands = emptyList(),
            runCommands = runCommands,
            runEntryCandidates = runEntryCandidates,
            notes = emptyList(),
        )
}

private object ProjectRunServiceInvariants {
    fun inferredRunCommand(context: PythonProjectAnalyzer.PythonProjectContext): String? =
        context.runCommands.firstOrNull()?.takeIf { it.isNotBlank() }

    fun noCommandSummary(context: PythonProjectAnalyzer.PythonProjectContext): String {
        val candidates = context.runEntryCandidates.take(4)
        val candidateText = if (candidates.isEmpty()) {
            "Blueprint looked for FastAPI, Flask, Streamlit, __main__.py, app.py, main.py, and __name__ == \"__main__\" entrypoints."
        } else {
            "Open one of these likely entry files manually: ${candidates.joinToString(", ")}."
        }
        return "Blueprint could not infer a run command yet. Refresh UML From Code first, then verify the feature manually. $candidateText"
    }

    fun isRunning(process: Process?): Boolean = process?.isAlive == true

    class RunGuard {
        private var launchToken = 0L
        private var active: Process? = null

        fun beginLaunch(): Long {
            launchToken += 1
            return launchToken
        }

        fun attachProcess(launchId: Long, process: Process): AttachmentResult {
            if (launchId != launchToken) {
                stop(process)
                return AttachmentResult(attached = false, stoppedPrevious = false)
            }
            val previous = active
            active = process
            val stoppedPrevious = if (previous != null && previous != process) {
                stop(previous)
                true
            } else {
                false
            }
            return AttachmentResult(attached = true, stoppedPrevious = stoppedPrevious)
        }

        fun stopActiveProcess(): Boolean {
            val process = active ?: return false
            active = null
            stop(process)
            return true
        }

        fun isRunning(): Boolean = active?.isAlive == true

        private fun stop(process: Process) {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }

        data class AttachmentResult(
            val attached: Boolean,
            val stoppedPrevious: Boolean,
        )
    }
}
