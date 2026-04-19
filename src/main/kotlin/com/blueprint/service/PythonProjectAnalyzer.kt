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
        val runCommands: List<String>,
        val notes: List<String>,
    ) {
        fun isPythonLikely(): Boolean =
            configFiles.isNotEmpty() || sourceRoots.isNotEmpty() || testRoots.isNotEmpty()

        /**
         * Returns a short plain-English checklist for the Python context panel.
         */
        fun summaryChecklist(): String =
            buildString {
                appendLine("Python quick checklist")
                appendLine("- Source roots: ${sourceRoots.joinToString(", ").ifBlank { "No Python source roots detected yet." }}")
                appendLine("- Validation command: ${testCommands.firstOrNull() ?: "No validation command inferred yet."}")
                appendLine("- Run command: ${runCommands.firstOrNull() ?: "No run command inferred yet."}")
            }.trim()

        /**
         * Returns the structured Python project context used in the context panel and prompts.
         */
        fun promptContext(): String =
            buildString {
                appendLine(summaryChecklist())
                appendLine()
                appendLine("PYTHON_PROJECT_CONTEXT")
                appendLine("isPythonLikely: ${isPythonLikely()}")
                appendLine("packageManager: $packageManager")
                appendLine("configFiles: ${configFiles.joinToString(", ").ifBlank { "(none found)" }}")
                appendLine("sourceRoots: ${sourceRoots.joinToString(", ").ifBlank { "(none found)" }}")
                appendLine("testRoots: ${testRoots.joinToString(", ").ifBlank { "(none found)" }}")
                appendLine("frameworkHints: ${frameworks.joinToString(", ").ifBlank { "(none found)" }}")
                appendLine("suggestedTestCommands: ${testCommands.joinToString(" && ").ifBlank { "(none inferred)" }}")
                appendLine("suggestedRunCommands: ${runCommands.joinToString(" && ").ifBlank { "(none inferred)" }}")
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
            val runCommands = inferRunCommands(base, sourceRoots, frameworks)
            val notes = buildList {
                if ("pytest" !in frameworks && testRoots.isNotEmpty()) {
                    add("Test roots exist but pytest dependency was not detected; Blueprint will validate with a built-in fallback or import/compile checks if needed.")
                }
                if (sourceRoots.isEmpty()) {
                    add("No Python source roots were detected; open a folder with .py files, src/, app/, or package directories so Blueprint can map code to UML.")
                }
                if (configFiles.isEmpty()) {
                    add("No pyproject/setup/requirements files were detected; infer dependencies from local files only.")
                }
                if (sourceRoots.size > 6) {
                    add("Multiple Python source roots were detected. Review the generated UML and scope changes to the relevant package before applying.")
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
                runCommands = runCommands,
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
        if (baseHasTopLevelPythonFiles(base)) roots += "."
        listOf("src", "app").forEach { rootName ->
            val root = base.resolve(rootName)
            if (!Files.isDirectory(root)) return@forEach
            roots += rootName
            Files.list(root).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .filter { !shouldSkipDir(it.fileName.toString()) }
                    .filter { containsPythonSources(it, maxDepth = 2) }
                    .forEach { roots += base.relativize(it).toString().replace('\\', '/') }
            }
        }
        Files.list(base).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .filter { !shouldSkipDir(it.fileName.toString()) }
                .filter { containsPythonSources(it, maxDepth = 2) }
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
            "attrs" to listOf("attrs", "import attr", "from attr", "from attrs", "@define", "@attr.s"),
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
        val commands = mutableListOf<String>()
        if ("pytest" in frameworks || configFiles.any { it == "pytest.ini" || it == "setup.cfg" || it == "pyproject.toml" }) {
            commands += when (packageManager) {
                "uv" -> "uv run pytest"
                "poetry" -> "poetry run pytest"
                "pipenv" -> "pipenv run pytest"
                else -> "python -m pytest"
            }
        }
        if (testRoots.isNotEmpty()) {
            commands += "python -m unittest discover ${testRoots.first()}"
        }
        if ("tox.ini" in configFiles) commands += "tox"
        if ("noxfile.py" in configFiles) commands += "nox"
        return commands.distinct()
    }

    private fun inferRunCommands(base: Path, sourceRoots: List<String>, frameworks: List<String>): List<String> {
        val commands = mutableListOf<String>()
        val pythonFiles = walkPythonFiles(base, 4)
        val relPaths = pythonFiles.map { base.relativize(it).toString().replace('\\', '/') }
        val sourcePackages = sourceRoots.filter { it != "." }
        val candidatePackages = sourcePackages.filterNot { it == "src" || it == "app" || it == "test" || it == "tests" }
        val detectedPackage = candidatePackages.firstOrNull() ?: sourcePackages.firstOrNull { it != "src" && it != "app" }
        val appLikePath = relPaths.firstOrNull { it == "app.py" || it.endsWith("/app.py") || it == "main.py" || it.endsWith("/main.py") }
        val mainGuardPath = relPaths.firstOrNull { hasMainGuard(base.resolve(it)) }
        val packageRunCommand = preferredPackageRunCommand(base, candidatePackages, detectedPackage)

        if ("fastapi" in frameworks) {
            val fastApiPath = relPaths.firstOrNull {
                hasAnyText(base.resolve(it), listOf("FastAPI(", "fastapi.FastAPI(", "APIRouter(", "from fastapi import"))
            }
            val fastApiModule = fastApiPath?.let { inferFrameworkModule(base, it, sourcePackages, "server") }
            if (fastApiModule != null) {
                commands += "uvicorn $fastApiModule:app --reload"
            }
            fastApiPath?.let { path ->
                commands += "python ${inferPreferredPythonPath(base, path, sourcePackages, "server")}" }
        }
        if ("flask" in frameworks) {
            detectedPackage?.let { commands += "flask --app ${it.replace('/', '.')}.api run" }
            if (commands.none { it.startsWith("flask --app ") }) {
                relPaths.firstOrNull {
                    hasAnyText(base.resolve(it), listOf("Flask(", "flask.Flask(", "Blueprint(", "from flask import"))
                }?.let { path ->
                    commands += "flask --app ${inferFrameworkModule(base, path, sourcePackages, "api") ?: moduleName(path)} run"
                }
            }
        }
        relPaths.firstOrNull { hasAnyText(base.resolve(it), listOf("streamlit.", "import streamlit")) }
            ?.let { path -> commands += "streamlit run $path" }
        appLikePath?.let { commands += "python $it" }
        if (commands.isEmpty()) {
            mainGuardPath?.let { commands += "python $it" }
            packageRunCommand?.let { commands += it }
        }
        if (commands.isEmpty()) {
            packageRunCommand?.let { commands += it }
        }
        return commands.distinct()
    }

    private fun preferredPackageRunCommand(base: Path, candidatePackages: List<String>, detectedPackage: String?): String? {
        val preferredPackage = candidatePackages.firstOrNull { isImportablePackage(base.resolve(it)) }
            ?: candidatePackages.firstOrNull()
            ?: detectedPackage
            ?: return null
        val moduleName = packageModuleName(preferredPackage) ?: return null
        return "python -m $moduleName"
    }

    private fun packageModuleName(packagePath: String): String? =
        packagePath.trim('/').replace('/', '.').takeIf { it.isNotBlank() }

    private fun hasMainGuard(path: Path): Boolean =
        hasAnyText(path, listOf("if __name__ == '__main__':", "if __name__ == \"__main__\":"))

    private fun hasAnyText(path: Path, needles: List<String>): Boolean {
        val text = readSmall(path, 20_000)
        return text.isNotBlank() && needles.any { text.contains(it) }
    }

    private fun moduleName(path: String): String =
        path.removeSuffix(".py").replace('/', '.')

    private fun inferFrameworkModule(base: Path, path: String, sourcePackages: List<String>, preferredLeaf: String): String? {
        val normalizedPath = path.removeSuffix(".py")
        val parent = normalizedPath.substringBeforeLast('/', "")
        val sourcePackage = sourcePackages.firstOrNull { candidate ->
            normalizedPath == candidate || normalizedPath.startsWith("$candidate/")
        } ?: return moduleName(path)
        val relativeToSource = normalizedPath.removePrefix(sourcePackage).removePrefix("/")
        val preferredModule = preferredModulePath(base, sourcePackage, parent, preferredLeaf)
        return when {
            relativeToSource.isBlank() -> sourcePackage.replace('/', '.')
            preferredModule != null -> preferredModule.replace('/', '.')
            else -> moduleName(path)
        }
    }

    private fun inferPreferredPythonPath(base: Path, path: String, sourcePackages: List<String>, preferredLeaf: String): String {
        val sourcePackage = sourcePackages.firstOrNull { candidate ->
            path == "$candidate.py" || path.startsWith("$candidate/")
        } ?: return path
        val parent = path.removeSuffix(".py").substringBeforeLast('/', "")
        val preferredModule = preferredModulePath(base, sourcePackage, parent, preferredLeaf) ?: return path
        return "$preferredModule.py"
    }

    private fun preferredModulePath(base: Path, sourcePackage: String, parent: String, preferredLeaf: String): String? {
        val parentDir = if (parent.isBlank()) base else base.resolve(parent)
        if (Files.isRegularFile(parentDir.resolve("$preferredLeaf.py"))) {
            return listOf(parent, preferredLeaf).filter { it.isNotBlank() }.joinToString("/")
        }
        val sourceDir = base.resolve(sourcePackage)
        if (Files.isRegularFile(sourceDir.resolve("$preferredLeaf.py"))) {
            return listOf(sourcePackage, preferredLeaf).joinToString("/")
        }
        if (parent.isNotBlank()) {
            return listOf(parent, preferredLeaf).joinToString("/")
        }
        return if (sourcePackage.isNotBlank()) listOf(sourcePackage, preferredLeaf).joinToString("/") else null
    }

    private fun isImportablePackage(path: Path): Boolean {
        if (!Files.isDirectory(path)) return false
        return Files.isRegularFile(path.resolve("__main__.py")) ||
            Files.isRegularFile(path.resolve("__init__.py")) ||
            containsPythonSources(path, maxDepth = 2)
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
        if (lastPackage != null) {
            return base.relativize(lastPackage).toString().replace('\\', '/')
        }
        return namespaceSourceRoot(base, file)
    }

    private fun namespaceSourceRoot(base: Path, file: Path): String? {
        val parent = file.parent ?: return null
        if (parent == base) return null
        val relative = runCatching { base.relativize(parent) }.getOrNull() ?: return null
        val parts = relative.map { it.toString() }
        if (parts.isEmpty()) return null
        val first = parts.first()
        return when (first) {
            "src", "app" -> first
            "test", "tests" -> null
            else -> first
        }
    }

    private fun baseHasTopLevelPythonFiles(base: Path): Boolean {
        Files.list(base).use { stream ->
            return stream.anyMatch { path ->
                Files.isRegularFile(path) && path.fileName.toString().endsWith(".py")
            }
        }
    }

    private fun containsPythonSources(dir: Path, maxDepth: Int): Boolean {
        if (!Files.isDirectory(dir)) return false
        Files.walk(dir, maxDepth).use { stream ->
            return stream.anyMatch { path ->
                Files.isRegularFile(path) &&
                    path.fileName.toString().endsWith(".py") &&
                    !baseRelativeParts(dir, path).any { shouldSkipDir(it) }
            }
        }
    }

    private fun baseRelativeParts(base: Path, path: Path): List<String> =
        runCatching { base.relativize(path).map { it.toString() }.toList() }.getOrDefault(emptyList())

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
            runCommands = emptyList(),
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
            "requirements.in",
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
            "generated",
            "node_modules",
            "site-packages",
            "vendor",
            "venv",
        )
    }
}
