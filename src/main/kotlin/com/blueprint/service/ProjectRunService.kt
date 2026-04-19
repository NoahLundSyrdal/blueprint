package com.blueprint.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs an inferred project command and streams output back to the UI.
 */
@Service(Service.Level.PROJECT)
class ProjectRunService(private val project: Project) {
    data class RunState(
        val status: Status,
        val command: String,
        val output: String,
        val exitCode: Int? = null,
        val summary: String = "",
    ) {
        enum class Status { IDLE, STARTING, RUNNING, FINISHED, FAILED, STOPPED }
    }

    private val launchToken = AtomicLong(0)
    private val activeProcess = AtomicReference<Process?>(null)

    /**
     * Returns the currently inferred run command for the open Python project, if available.
     */
    fun inferredRunCommand(context: PythonProjectAnalyzer.PythonProjectContext = project.getService(PythonProjectAnalyzer::class.java).analyze()): String? =
        context.runCommands.firstOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Returns true when a process launched by this service is still running.
     */
    fun isRunning(): Boolean = activeProcess.get()?.isAlive == true

    /**
     * Starts the inferred run command and streams stdout/stderr snapshots through [onUpdate].
     */
    fun runInferredCommand(onUpdate: (RunState) -> Unit) {
        val context = project.getService(PythonProjectAnalyzer::class.java).analyze()
        val command = inferredRunCommand(context)
        if (command == null) {
            onUi { onUpdate(RunState(RunState.Status.FAILED, "", "", summary = noCommandSummary())) }
            return
        }
        val basePath = context.basePath.ifBlank { project.basePath.orEmpty() }
        if (basePath.isBlank()) {
            onUi { onUpdate(RunState(RunState.Status.FAILED, command, "", summary = "Blueprint could not run the app because the project base path is missing.")) }
            return
        }
        val launchId = launchToken.incrementAndGet()
        onUi { onUpdate(RunState(RunState.Status.STARTING, command, "", summary = "Starting inferred run command: $command")) }
        ApplicationManager.getApplication().executeOnPooledThread {
            val output = StringBuilder()
            try {
                val process = ProcessBuilder("/bin/sh", "-lc", command)
                    .directory(Paths.get(basePath).toFile())
                    .redirectErrorStream(true)
                    .start()
                attachActiveProcess(launchId, process)
                onUi { onUpdate(RunState(RunState.Status.RUNNING, command, "", summary = "Running inferred command: $command")) }
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        output.appendLine(line)
                        val snapshot = output.toString().trimEnd()
                        onUi { onUpdate(RunState(RunState.Status.RUNNING, command, snapshot, summary = "Streaming app output for: $command")) }
                    }
                }
                val finished = process.waitFor(2, TimeUnit.SECONDS)
                clearActiveProcess(process)
                val snapshot = output.toString().trimEnd()
                if (!finished) {
                    process.destroy()
                    process.waitFor(2, TimeUnit.SECONDS)
                }
                val exitCode = runCatching { process.exitValue() }.getOrNull()
                val status = when {
                    exitCode == 0 -> RunState.Status.FINISHED
                    exitCode == null -> RunState.Status.STOPPED
                    else -> RunState.Status.FAILED
                }
                val summary = when (status) {
                    RunState.Status.FINISHED -> "Run finished successfully: $command"
                    RunState.Status.STOPPED -> "Run stopped: $command"
                    RunState.Status.FAILED -> "Run failed with exit ${exitCode ?: "unknown"}: $command"
                    else -> "Run finished: $command"
                }
                onUi { onUpdate(RunState(status, command, snapshot, exitCode, summary)) }
            } catch (t: Throwable) {
                clearActiveProcess()
                val snapshot = output.toString().trimEnd()
                onUi {
                    onUpdate(
                        RunState(
                            status = RunState.Status.FAILED,
                            command = command,
                            output = snapshot,
                            summary = "Blueprint could not run the app: ${t.message ?: t.javaClass.simpleName}",
                        )
                    )
                }
            }
        }
    }

    /**
     * Stops the active inferred run command, if one exists.
     */
    fun stopRun(): Boolean = stopProcess(activeProcess.getAndSet(null))

    /**
     * Returns the safe fallback message shown when no run command can be inferred.
     */
    fun noCommandSummary(): String =
        "Blueprint could not infer a run command yet. Refresh UML From Code first, or run the project entrypoint manually."

    private fun attachActiveProcess(launchId: Long, process: Process) {
        if (launchId != launchToken.get()) {
            stopProcess(process)
            return
        }
        val previous = activeProcess.getAndSet(process)
        if (previous != null && previous != process) {
            stopProcess(previous)
        }
    }

    private fun clearActiveProcess(process: Process? = null) {
        if (process == null) {
            activeProcess.set(null)
            return
        }
        activeProcess.compareAndSet(process, null)
    }

    private fun stopProcess(process: Process?): Boolean {
        process ?: return false
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
        return true
    }

    private fun onUi(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action)
    }
}
