package com.blueprint.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

/**
 * Lightweight Python project analyzer for PyCharm-first Blueprint flows.
 *
 * This deliberately avoids PSI for now. It uses stable project files and
 * directory structure to give prompts concrete Python context.
 */
@Service(Service.Level.PROJECT)
class PythonProjectAnalyzer(private val project: Project) {

    data class PythonProjectContext(
        val basePath: String,
        val configFiles: List<String>,
        val sourceRoots: List<String>,
        val testRoots: List<String>,
        val packageManager: String,
        val frameworks: List<String>,
        val testCommands: List<String>,
        val notes: List<String>,
    ) {
        fun isPythonLikely(): Boolean =
            configFiles.isNotEmpty() || sourceRoots.isNotEmpty() || testRoots.isNotEmpty()

        fun promptContext(): String =
            buildString {
                appendLine("PYTHON_PROJECT_CONTEXT")
                appendLine("isPythonLikely: ${isPythonLikely()}")
                appendLine("packageManager: $packageManager")
                appendLine("configFiles: ${configFiles.joinToString(", ").ifBlank { "(none found)" }}")
                appendLine("sourceRoots: ${sourceRoots.joinToString(", ").ifBlank { "(none found)" }}")
                appendLine("testRoots: ${testRoots.joinToString(", ").ifBlank { "(none found)" }}")
                appendLine("frameworkHints: ${frameworks.joinToString(", ").ifBlank { "(none found)" }}")
                appendLine("suggestedTestCommands: ${testCommands.joinToString(" && ").ifBlank { "(none inferred)" }}")
                if (notes.isNotEmpty()) {
                    appendLine("notes:")
                    notes.forEach { appendLine("- $it") }
                }
                appendLine("Guidance:")
                appendLine("- Prefer Python modules under the detected source roots.")
                appendLine("- Prefer pytest-style tests under detected test roots when tests are in scope.")
                appendLine("- Keep generated files inside the Blueprint node file scope.")
                appendLine("- Preserve existing Python framework and package-manager conventions.")
            }.trim()
    }

    private val log = Logger.getInstance(PythonProjectAnalyzer::class.java)

    fun analyze(maxDepth: Int = 4): PythonProjectContext {
        val basePath = project.basePath ?: return emptyContext("")
        val base = Paths.get(basePath).normalize()
        if (!Files.isDirectory(base)) return emptyContext(basePath)

        return try {
            val configFiles = detectConfigFiles(base)
            val text = configFiles.joinToString("\n\n") { rel ->
                readSmall(base.resolve(rel), 80_000)
            }
            val sourceRoots = detectSourceRoots(base, maxDepth)
            val testRoots = detectTestRoots(base, maxDepth)
            val frameworks = detectFrameworks(text, base)
            val packageManager = detectPackageManager(configFiles, text)
            val testCommands = inferTestCommands(packageManager, configFiles, frameworks, testRoots)
            val notes = buildList {
                if ("pytest" !in frameworks && testRoots.isNotEmpty()) {
                    add("Test roots exist but pytest dependency was not detected; verify test runner before generating tests.")
                }
                if (sourceRoots.isEmpty()) {
                    add("No package roots with __init__.py were detected; generated Python file scopes should be explicit.")
                }
                if (configFiles.isEmpty()) {
                    add("No pyproject/setup/requirements files were detected; infer dependencies from local files only.")
                }
            }
            PythonProjectContext(
                basePath = basePath,
                configFiles = configFiles,
                sourceRoots = sourceRoots,
                testRoots = testRoots,
                packageManager = packageManager,
                frameworks = frameworks,
                testCommands = testCommands,
                notes = notes,
            )
        } catch (t: Throwable) {
            log.warn("Failed analyzing Python project", t)
            emptyContext(basePath).copy(notes = listOf("Python analyzer failed: ${t.message ?: t.javaClass.simpleName}"))
        }
    }

    private fun detectConfigFiles(base: Path): List<String> =
        knownConfigFiles.filter { Files.isRegularFile(base.resolve(it)) }

    private fun detectSourceRoots(base: Path, maxDepth: Int): List<String> {
        val roots = mutableSetOf<String>()
        val src = base.resolve("src")
        if (Files.isDirectory(src)) {
            Files.list(src).use { stream ->
                stream.filter { Files.isDirectory(it) && Files.isRegularFile(it.resolve("__init__.py")) }
                    .forEach { roots += base.relativize(it).toString().replace('\\', '/') }
            }
        }
        Files.list(base).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .filter { !shouldSkipDir(it.fileName.toString()) }
                .filter { Files.isRegularFile(it.resolve("__init__.py")) }
                .forEach { roots += base.relativize(it).toString().replace('\\', '/') }
        }
        walkPythonFiles(base, maxDepth)
            .mapNotNull { nearestPackageRoot(base, it) }
            .forEach { roots += it }
        return roots.sorted()
    }

    private fun detectTestRoots(base: Path, maxDepth: Int): List<String> {
        val roots = mutableSetOf<String>()
        listOf("tests", "test").forEach { rel ->
            if (Files.isDirectory(base.resolve(rel))) roots += rel
        }
        walkPythonFiles(base, maxDepth)
            .filter { it.fileName.toString().startsWith("test_") || it.fileName.toString().endsWith("_test.py") }
            .map { base.relativize(it.parent).toString().replace('\\', '/') }
            .filter { it.isNotBlank() && !it.startsWith(".") }
            .forEach { roots += it }
        return roots.sorted()
    }

    private fun detectFrameworks(configText: String, base: Path): List<String> {
        val haystack = buildString {
            append(configText.lowercase(Locale.US))
            append('\n')
            walkPythonFiles(base, 3).take(40).forEach { file ->
                append(readSmall(file, 8_000).lowercase(Locale.US))
                append('\n')
            }
        }
        return linkedMapOf(
            "pytest" to listOf("pytest", "[tool.pytest", "import pytest"),
            "fastapi" to listOf("fastapi", "from fastapi", "import fastapi"),
            "django" to listOf("django", "django-admin", "manage.py"),
            "flask" to listOf("flask", "from flask", "import flask"),
            "pydantic" to listOf("pydantic", "basemodel"),
            "sqlalchemy" to listOf("sqlalchemy", "declarative_base"),
            "typer" to listOf("typer", "import typer"),
            "click" to listOf("click", "import click"),
        ).filter { (_, needles) -> needles.any { haystack.contains(it) } }
            .keys
            .toList()
    }

    private fun detectPackageManager(configFiles: List<String>, text: String): String {
        val lower = text.lowercase(Locale.US)
        return when {
            "uv.lock" in configFiles || lower.contains("[tool.uv") -> "uv"
            "poetry.lock" in configFiles || lower.contains("[tool.poetry") -> "poetry"
            "Pipfile" in configFiles -> "pipenv"
            "requirements.txt" in configFiles || configFiles.any { it.startsWith("requirements") } -> "pip"
            "pyproject.toml" in configFiles -> "pyproject"
            else -> "unknown"
        }
    }

    private fun inferTestCommands(
        packageManager: String,
        configFiles: List<String>,
        frameworks: List<String>,
        testRoots: List<String>,
    ): List<String> {
        if ("pytest" !in frameworks && testRoots.isEmpty()) return emptyList()
        val pytest = when (packageManager) {
            "uv" -> "uv run pytest"
            "poetry" -> "poetry run pytest"
            "pipenv" -> "pipenv run pytest"
            else -> "python -m pytest"
        }
        val commands = mutableListOf(pytest)
        if ("tox.ini" in configFiles) commands += "tox"
        if ("noxfile.py" in configFiles) commands += "nox"
        return commands.distinct()
    }

    private fun walkPythonFiles(base: Path, maxDepth: Int): List<Path> {
        if (!Files.isDirectory(base)) return emptyList()
        Files.walk(base, maxDepth).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".py") }
                .filter { path -> base.relativize(path).none { part -> shouldSkipDir(part.toString()) } }
                .toList()
        }
    }

    private fun nearestPackageRoot(base: Path, file: Path): String? {
        var current = file.parent ?: return null
        var lastPackage: Path? = null
        while (current.startsWith(base) && current != base) {
            if (Files.isRegularFile(current.resolve("__init__.py"))) {
                lastPackage = current
                current = current.parent ?: break
            } else {
                break
            }
        }
        return lastPackage?.let { base.relativize(it).toString().replace('\\', '/') }
    }

    private fun readSmall(path: Path, maxChars: Int): String =
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > 600_000L) ""
            else Files.readString(path, StandardCharsets.UTF_8).take(maxChars)
        } catch (_: Throwable) {
            ""
        }

    private fun emptyContext(basePath: String): PythonProjectContext =
        PythonProjectContext(
            basePath = basePath,
            configFiles = emptyList(),
            sourceRoots = emptyList(),
            testRoots = emptyList(),
            packageManager = "unknown",
            frameworks = emptyList(),
            testCommands = emptyList(),
            notes = emptyList(),
        )

    private fun shouldSkipDir(name: String): Boolean =
        name in skippedDirectories || name.startsWith(".")

    private companion object {
        val knownConfigFiles = listOf(
            "pyproject.toml",
            "setup.py",
            "setup.cfg",
            "requirements.txt",
            "requirements-dev.txt",
            "dev-requirements.txt",
            "poetry.lock",
            "uv.lock",
            "Pipfile",
            "tox.ini",
            "noxfile.py",
            "pytest.ini",
        )
        val skippedDirectories = setOf(
            ".git",
            ".gradle",
            ".idea",
            ".mypy_cache",
            ".pytest_cache",
            ".ruff_cache",
            ".tox",
            ".venv",
            "__pycache__",
            "build",
            "dist",
            "node_modules",
            "site-packages",
            "venv",
        )
    }
}
