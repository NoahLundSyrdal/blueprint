package com.blueprint.service

import com.blueprint.model.Patch
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class ProjectValidationService(private val project: Project) {
    private val log = Logger.getInstance(ProjectValidationService::class.java)

    data class ValidationResult(
        val status: Status,
        val command: String = "",
        val exitCode: Int? = null,
        val outputExcerpt: String = "",
        val relatedFiles: List<String> = emptyList(),
        val durationMillis: Long = 0,
        val reason: String = "",
    ) {
        enum class Status { PASS, FAIL, SKIPPED }

        fun summaryLine(): String =
            when (status) {
                Status.PASS -> "Validation passed: $command"
                Status.FAIL -> "Validation failed: $command"
                Status.SKIPPED -> "Validation skipped: $reason"
            }
    }

    fun selectedCommand(context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze()): String? =
        chooseValidationCommand(context)

    fun validateAfterApply(patches: List<Patch>, timeoutSeconds: Long = 45): ValidationResult {
        val context = project.service<PythonProjectAnalyzer>().analyze()
        val command = chooseValidationCommand(context)
            ?: return ValidationResult(
                status = ValidationResult.Status.SKIPPED,
                reason = "No Python validation command was inferred for this project.",
                relatedFiles = patches.map { it.path }.distinct(),
            )
        val basePath = context.basePath.ifBlank { project.basePath.orEmpty() }
        if (basePath.isBlank()) {
            return ValidationResult(
                status = ValidationResult.Status.SKIPPED,
                command = command,
                reason = "No project base path is available.",
                relatedFiles = patches.map { it.path }.distinct(),
            )
        }

        val started = System.currentTimeMillis()
        return try {
            val primary = runCommand(command, basePath, timeoutSeconds)
            val finalRun = if (
                primary.exitCode != 0 &&
                isMissingPytest(primary.output) &&
                context.testRoots.isNotEmpty()
            ) {
                val fallback = runCommand(PYTEST_FALLBACK_COMMAND, basePath, timeoutSeconds)
                fallback.copy(
                    command = "$command; built-in test fallback",
                    output = buildString {
                        appendLine(primary.output.trim())
                        appendLine()
                        appendLine("pytest was not available; Blueprint ran its built-in test-function fallback.")
                        append(fallback.output.trim())
                    }.trim(),
                )
            } else {
                primary
            }

            if (finalRun.timedOut) {
                return ValidationResult(
                    status = ValidationResult.Status.FAIL,
                    command = finalRun.command,
                    exitCode = null,
                    outputExcerpt = "Validation timed out after ${timeoutSeconds}s.",
                    relatedFiles = relatedFiles(basePath, patches, ""),
                    durationMillis = finalRun.durationMillis,
                    reason = "timeout",
                )
            }
            ValidationResult(
                status = if (finalRun.exitCode == 0) ValidationResult.Status.PASS else ValidationResult.Status.FAIL,
                command = finalRun.command,
                exitCode = finalRun.exitCode,
                outputExcerpt = summarizeOutput(finalRun.output),
                relatedFiles = relatedFiles(basePath, patches, finalRun.output),
                durationMillis = finalRun.durationMillis,
                reason = if (finalRun.exitCode == 0) "" else "exit ${finalRun.exitCode}",
            )
        } catch (t: Throwable) {
            log.warn("Project validation failed to run", t)
            ValidationResult(
                status = ValidationResult.Status.FAIL,
                command = command,
                outputExcerpt = t.message ?: t.javaClass.simpleName,
                relatedFiles = patches.map { it.path }.distinct(),
                reason = t.javaClass.simpleName,
                durationMillis = System.currentTimeMillis() - started,
            )
        }
    }

    private fun runCommand(command: String, basePath: String, timeoutSeconds: Long): CommandRun {
        val started = System.currentTimeMillis()
        val process = ProcessBuilder("/bin/sh", "-lc", command)
            .directory(Paths.get(basePath).toFile())
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        val elapsed = System.currentTimeMillis() - started
        if (!finished) {
            process.destroyForcibly()
            return CommandRun(command, exitCode = null, output = "", durationMillis = elapsed, timedOut = true)
        }
        val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        return CommandRun(command, process.exitValue(), output, elapsed, timedOut = false)
    }

    fun validateAfterApplyAsync(
        patches: List<Patch>,
        timeoutSeconds: Long = 45,
        onDone: (ValidationResult) -> Unit,
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = validateAfterApply(patches, timeoutSeconds)
            ApplicationManager.getApplication().invokeLater { onDone(result) }
        }
    }

    private fun relatedFiles(basePath: String, patches: List<Patch>, output: String): List<String> {
        val patched = patches.map { it.path }
        val mentioned = PYTHON_FILE.findAll(output)
            .map { it.value.replace('\\', '/') }
            .mapNotNull { path ->
                val absolute = runCatching { Paths.get(path) }.getOrNull()
                if (absolute != null && absolute.isAbsolute) {
                    runCatching {
                        Paths.get(basePath).normalize().relativize(absolute.normalize()).toString().replace('\\', '/')
                    }.getOrNull()
                } else {
                    path.removePrefix("./")
                }
            }
        return (patched + mentioned).filter { it.isNotBlank() }.distinct().take(8)
    }

    private data class CommandRun(
        val command: String,
        val exitCode: Int?,
        val output: String,
        val durationMillis: Long,
        val timedOut: Boolean,
    )

    companion object {
        private val PYTHON_FILE = Regex("""(?:[A-Za-z]:)?[./\\w\\-\\s]+\\.py""")

        fun chooseValidationCommand(context: PythonProjectAnalyzer.PythonProjectContext): String? {
            val inferred = context.testCommands
                .firstOrNull { it.contains("pytest") }
                ?: context.testCommands.firstOrNull()
            if (!inferred.isNullOrBlank()) return inferred
            return when {
                context.testRoots.isNotEmpty() || context.configFiles.any { it == "pytest.ini" || it == "pyproject.toml" } -> "python -m pytest"
                context.sourceRoots.isNotEmpty() -> IMPORT_COMPILE_VALIDATION_COMMAND
                else -> null
            }
        }

        fun summarizeOutput(output: String, maxLines: Int = 14, maxChars: Int = 2_200): String {
            val cleaned = output
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .lines()
                .dropWhile { it.isBlank() }
                .dropLastWhile { it.isBlank() }
            if (cleaned.isEmpty()) return "(no output)"

            val interesting = cleaned.filter { line ->
                val lower = line.lowercase()
                lower.contains("failed") ||
                    lower.contains("error") ||
                    lower.contains("traceback") ||
                    lower.contains("no module named") ||
                    lower.contains(".py") ||
                    lower.contains("passed") ||
                    lower.startsWith("===") ||
                    lower.startsWith("___")
            }
            val lines = (if (interesting.isNotEmpty()) interesting else cleaned.takeLast(maxLines)).take(maxLines)
            val excerpt = lines.joinToString("\n")
            return if (excerpt.length <= maxChars) excerpt else excerpt.take(maxChars).trimEnd() + "\n..."
        }

        fun isMissingPytest(output: String): Boolean =
            output.contains("No module named pytest", ignoreCase = true) ||
                output.contains("No module named 'pytest'", ignoreCase = true)

        private val PYTEST_FALLBACK_COMMAND = """
            python - <<'PY'
            import importlib.util
            import pathlib
            import sys
            import traceback

            root = pathlib.Path.cwd()
            sys.path.insert(0, str(root))
            paths = sorted(set(root.glob("tests/test_*.py")) | set(root.glob("tests/**/*_test.py")))
            ran = 0
            failures = 0

            for path in paths:
                module_name = "blueprint_validation_" + "_".join(path.relative_to(root).with_suffix("").parts)
                spec = importlib.util.spec_from_file_location(module_name, path)
                module = importlib.util.module_from_spec(spec)
                try:
                    spec.loader.exec_module(module)
                except Exception:
                    failures += 1
                    print(f"ERROR {path.relative_to(root)}")
                    traceback.print_exc()
                    continue
                for name, fn in sorted(vars(module).items()):
                    if name.startswith("test_") and callable(fn):
                        ran += 1
                        try:
                            fn()
                        except Exception:
                            failures += 1
                            print(f"FAILED {path.relative_to(root)}::{name}")
                            traceback.print_exc()

            if ran == 0:
                print("No test functions found by Blueprint fallback runner.")
                sys.exit(5)
            if failures:
                print(f"{failures} failed, {ran - failures} passed via Blueprint fallback runner")
                sys.exit(1)
            print(f"{ran} passed via Blueprint fallback runner")
            PY
        """.trimIndent()

        private val IMPORT_COMPILE_VALIDATION_COMMAND = """
            python - <<'PY'
            import compileall
            import pathlib
            import sys

            root = pathlib.Path.cwd()
            source_roots = [p for p in [root / "app", root / "src"] if p.exists()]
            if not source_roots:
                source_roots = [
                    path.parent for path in root.rglob("*.py")
                    if ".venv" not in path.parts and "venv" not in path.parts and "site-packages" not in path.parts
                ]
            checked = []
            failures = []

            for candidate in source_roots:
                if not candidate.exists() or not candidate.is_dir():
                    continue
                if candidate in checked:
                    continue
                checked.append(candidate)
                ok = compileall.compile_dir(str(candidate), quiet=1, force=False)
                if not ok:
                    failures.append(str(candidate.relative_to(root) if candidate != root else candidate))

            if not checked:
                print("No Python source roots found for import/compile validation.")
                sys.exit(5)
            if failures:
                print("Import/compile validation failed for: " + ", ".join(failures))
                sys.exit(1)
            print("Import/compile validation passed for: " + ", ".join(str(path.relative_to(root) if path != root else path) for path in checked))
            PY
        """.trimIndent()
    }
}
