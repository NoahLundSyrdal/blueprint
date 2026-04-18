package com.blueprint.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

/**
 * Generates a lightweight Mermaid class diagram from the open Python project.
 *
 * This is intentionally file-based for the hackathon demo. It avoids PSI
 * dependencies while still giving the product a real "project -> UML -> nodes"
 * loop that works in PyCharm Community.
 */
@Service(Service.Level.PROJECT)
class PythonUmlGenerator(private val project: Project) {

    data class GeneratedUml(
        val text: String,
        val classCount: Int,
        val relationshipCount: Int,
        val filesScanned: Int,
        val warnings: List<String>,
    )

    private data class PythonClass(
        val name: String,
        val bases: List<String>,
        val relPath: String,
        val fields: MutableMap<String, String> = linkedMapOf(),
        val methods: MutableSet<String> = linkedSetOf(),
    )

    private data class Relationship(
        val from: String,
        val label: String,
        val to: String,
    )

    private val log = Logger.getInstance(PythonUmlGenerator::class.java)

    fun generate(maxDepth: Int = 8): GeneratedUml {
        val basePath = project.basePath
        if (basePath.isNullOrBlank()) {
            return emptyResult("Project has no base path.")
        }
        val base = Paths.get(basePath).normalize()
        if (!Files.isDirectory(base)) {
            return emptyResult("Project base path is not a directory.")
        }

        return try {
            val context = project.service<PythonProjectAnalyzer>().analyze()
            val files = collectPythonFiles(base, context, maxDepth)
            val classes = files.flatMap { parseFile(base, it) }
                .distinctBy { "${it.relPath}:${it.name}" }
                .sortedWith(compareBy<PythonClass> { it.relPath }.thenBy { it.name })
            val relationships = inferRelationships(classes)
            val warnings = buildList {
                if (!context.isPythonLikely()) add("Python project context is sparse; UML was inferred from .py files only.")
                if (files.isEmpty()) add("No Python files were found in the current project.")
                if (classes.isEmpty()) add("No Python classes were found. Add a class or paste UML manually.")
                classes.filter { it.fields.isEmpty() && it.methods.isEmpty() }
                    .take(8)
                    .forEach { add("${it.name} has no parsed fields or public methods.") }
            }
            GeneratedUml(
                text = renderMermaid(classes, relationships, files.size, context),
                classCount = classes.size,
                relationshipCount = relationships.size,
                filesScanned = files.size,
                warnings = warnings,
            )
        } catch (t: Throwable) {
            log.warn("Failed generating Python UML", t)
            emptyResult("Python UML generation failed: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun collectPythonFiles(
        base: Path,
        context: PythonProjectAnalyzer.PythonProjectContext,
        maxDepth: Int,
    ): List<Path> {
        val roots = context.sourceRoots.ifEmpty {
            listOf("")
        }
        val files = roots.flatMap { rel ->
            val root = if (rel.isBlank()) base else base.resolve(rel).normalize()
            if (!Files.isDirectory(root) || !root.startsWith(base)) {
                emptyList()
            } else {
                Files.walk(root, maxDepth).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) }
                        .filter { it.fileName.toString().endsWith(".py") }
                        .filter { path -> !base.relativize(path).any { part -> shouldSkipDir(part.toString()) } }
                        .filter { path -> !isLikelyGenerated(path.fileName.toString()) }
                        .toList()
                }
            }
        }
        val unique = files.distinct().sorted()
        return if (unique.isNotEmpty()) unique else fallbackPythonFiles(base, maxDepth)
    }

    private fun fallbackPythonFiles(base: Path, maxDepth: Int): List<Path> =
        Files.walk(base, maxDepth).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".py") }
                .filter { path -> !base.relativize(path).any { part -> shouldSkipDir(part.toString()) } }
                .filter { path -> !isLikelyGenerated(path.fileName.toString()) }
                .toList()
                .distinct()
                .sorted()
        }

    private fun parseFile(base: Path, file: Path): List<PythonClass> {
        val rel = base.relativize(file).toString().replace('\\', '/')
        val text = readSmall(file)
        if (text.isBlank()) return emptyList()

        val classes = mutableListOf<PythonClass>()
        var current: PythonClass? = null
        var classIndent = 0

        fun closeCurrent() {
            current?.let { classes += it }
            current = null
        }

        text.lines().forEach { rawLine ->
            val header = classHeader.matchEntire(rawLine)
            if (header != null) {
                closeCurrent()
                classIndent = indentOf(header.groupValues[1])
                current = PythonClass(
                    name = header.groupValues[2],
                    bases = parseBases(header.groupValues.getOrElse(3) { "" }),
                    relPath = rel,
                )
                return@forEach
            }

            val cls = current ?: return@forEach
            val trimmed = rawLine.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("@")) return@forEach

            val indent = indentOf(rawLine.takeWhile { it == ' ' || it == '\t' })
            if (indent <= classIndent) {
                closeCurrent()
                return@forEach
            }

            methodRegex.find(rawLine)?.let { match ->
                val method = match.groupValues[1]
                if (method == "__init__" || !method.startsWith("_")) {
                    cls.methods += method
                }
                return@forEach
            }

            selfAnnotated.find(rawLine)?.let { match ->
                cls.addField(match.groupValues[1], cleanType(match.groupValues[2]))
            }
            selfAssigned.find(rawLine)?.let { match ->
                cls.addField(match.groupValues[1], inferTypeFromValue(match.groupValues[2]))
            }

            if (indent == classIndent + 4) {
                classAnnotated.matchEntire(rawLine)?.let { match ->
                    cls.addField(match.groupValues[1], cleanType(match.groupValues[2]))
                    return@forEach
                }
                classAssigned.matchEntire(rawLine)?.let { match ->
                    cls.addField(match.groupValues[1], inferTypeFromValue(match.groupValues[2]))
                }
            }
        }
        closeCurrent()
        return classes
    }

    private fun PythonClass.addField(name: String, type: String) {
        val field = name.trim()
        if (field.isBlank() || field.startsWith("_") || field in ignoredFields) return
        fields.putIfAbsent(field, type.ifBlank { "Any" })
    }

    private fun inferRelationships(classes: List<PythonClass>): List<Relationship> {
        val classNames = classes.map { it.name }.toSet()
        val relationships = linkedSetOf<Relationship>()
        classes.forEach { cls ->
            cls.bases
                .filter { it in classNames && it != cls.name }
                .forEach { relationships += Relationship(cls.name, "extends", it) }

            cls.fields.forEach { (fieldName, type) ->
                classNames
                    .filter { other -> other != cls.name && type.referencesClass(other) }
                    .forEach { other ->
                        val label = if (fieldName.endsWith("s", ignoreCase = true)) "contains" else "references"
                        relationships += Relationship(cls.name, label, other)
                    }
            }
        }
        return relationships.sortedWith(compareBy<Relationship> { it.from }.thenBy { it.to }.thenBy { it.label })
    }

    private fun renderMermaid(
        classes: List<PythonClass>,
        relationships: List<Relationship>,
        filesScanned: Int,
        context: PythonProjectAnalyzer.PythonProjectContext,
    ): String =
        buildString {
            appendLine("classDiagram")
            appendLine("%% Generated by Blueprint from project: ${project.name}")
            appendLine("%% Files scanned: $filesScanned")
            if (context.sourceRoots.isNotEmpty()) {
                appendLine("%% Source roots: ${context.sourceRoots.joinToString(", ")}")
            }
            appendLine()

            if (classes.isEmpty()) {
                appendLine("%% No Python classes were found.")
                appendLine("%% Add or paste Mermaid class blocks here before importing, for example:")
                appendLine("%%")
                appendLine("%% class Invite {")
                appendLine("%%   id: str")
                appendLine("%%   email: str")
                appendLine("%% }")
                return@buildString
            }

            classes.forEach { cls ->
                appendLine("class ${cls.name} {")
                cls.fields.entries.take(14).forEach { (name, type) ->
                    appendLine("  $name: ${type.toMermaidType()}")
                }
                cls.methods.take(10).forEach { method ->
                    appendLine("  +$method()")
                }
                appendLine("}")
                appendLine()
            }
            relationships.forEach { rel ->
                appendLine("${rel.from} --> ${rel.to} : ${rel.label}")
            }
        }.trim()

    private fun parseBases(raw: String): List<String> =
        raw.split(',')
            .map { it.substringBefore("[").substringAfterLast(".").trim() }
            .filter { it.matches(Regex("""[A-Z][A-Za-z0-9_]*""")) }
            .distinct()

    private fun cleanType(raw: String): String =
        raw.substringBefore("=")
            .substringBefore("#")
            .trim()
            .ifBlank { "Any" }

    private fun inferTypeFromValue(raw: String): String {
        val value = raw.substringBefore("#").trim()
        val constructor = Regex("""^([A-Z][A-Za-z0-9_]*)\s*\(""").find(value)?.groupValues?.getOrNull(1)
        if (constructor != null) return constructor
        return when {
            value.startsWith("\"") || value.startsWith("'") -> "str"
            value == "True" || value == "False" -> "bool"
            value.startsWith("[") -> "list"
            value.startsWith("{") -> "dict"
            value.matches(Regex("""-?\d+""")) -> "int"
            value.matches(Regex("""-?\d+\.\d+""")) -> "float"
            else -> "Any"
        }
    }

    private fun String.referencesClass(className: String): Boolean =
        Regex("""\b${Regex.escape(className)}\b""").containsMatchIn(this)

    private fun String.toMermaidType(): String =
        replace("{", "[")
            .replace("}", "]")
            .replace("<", "[")
            .replace(">", "]")
            .replace('\n', ' ')
            .trim()
            .ifBlank { "Any" }

    private fun readSmall(path: Path, maxChars: Int = 500_000): String =
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > 1_000_000L) ""
            else Files.readString(path, StandardCharsets.UTF_8).take(maxChars)
        } catch (_: Throwable) {
            ""
        }

    private fun emptyResult(warning: String): GeneratedUml =
        GeneratedUml(
            text = "classDiagram\n%% $warning\n%% Add Mermaid class blocks here before importing.",
            classCount = 0,
            relationshipCount = 0,
            filesScanned = 0,
            warnings = listOf(warning),
        )

    private fun indentOf(prefix: String): Int =
        prefix.fold(0) { acc, char -> acc + if (char == '\t') 4 else 1 }

    private fun shouldSkipDir(name: String): Boolean =
        name in skippedDirectories || name.startsWith(".")

    private fun isLikelyGenerated(fileName: String): Boolean =
        fileName.endsWith("_pb2.py") || fileName.endsWith("_pb2_grpc.py")

    private companion object {
        val classHeader = Regex("""^(\s*)class\s+([A-Z][A-Za-z0-9_]*)\s*(?:\(([^)]*)\))?\s*:\s*$""")
        val methodRegex = Regex("""^\s*(?:async\s+)?def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        val selfAnnotated = Regex("""self\.([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=#]+)""")
        val selfAssigned = Regex("""self\.([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^#]+)""")
        val classAnnotated = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=#]+).*$""")
        val classAssigned = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^#]+).*$""")
        val ignoredFields = setOf(
            "Config",
            "Meta",
            "model_config",
            "objects",
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
