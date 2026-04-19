package com.blueprint.service

import com.blueprint.model.NodeContract
import com.google.gson.Gson
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Renders the shared UML model module with small, AST-backed edits.
 *
 * It is intentionally conservative rather than a general Python formatter:
 * Python's ast module identifies existing class ranges and annotated fields.
 * Top-level imports/classes/functions outside the UML-owned dataclasses are
 * preserved, existing class methods stay in place, and generated fields for
 * the UML model classes are the only class body lines rewritten.
 */
object PythonModelModuleRenderer {
    private val gson = Gson()

    fun render(outputs: List<NodeContract>, existingContent: String = ""): String {
        return renderWithReport(outputs, existingContent).content
    }

    fun renderWithReport(outputs: List<NodeContract>, existingContent: String = ""): RenderResult {
        val classes = orderedModelOutputs(outputs)
        val mergedClasses = mergeExistingFieldDefaults(classes, existingContent)
        val imports = requiredImports(mergedClasses)
        val body = if (existingContent.isBlank()) {
            renderFreshModule(mergedClasses)
        } else {
            mergeIntoExisting(existingContent, mergedClasses)
        }
        val content = ensureImports(body, imports).trimEnd() + "\n"
        return RenderResult(
            content = content,
            changes = describeChanges(existingContent, content),
        )
    }

    fun describeChanges(existingContent: String, proposedContent: String): List<String> {
        val existingClasses = modelSpecsInContent(existingContent)
        val proposedClasses = modelSpecsInContent(proposedContent)
        val changes = mutableListOf<String>()

        proposedClasses.values.sortedBy { it.name }.forEach { proposed ->
            val existing = existingClasses[proposed.name]
            if (existing == null) {
                changes += "add class ${proposed.name}"
                proposed.fields.forEach { field -> changes += "add field ${proposed.name}.${field.name}" }
                return@forEach
            }

            val existingFields = existing.fields.associateBy { it.name }
            val proposedFields = proposed.fields.associateBy { it.name }
            proposed.fields.forEach { field ->
                val old = existingFields[field.name]
                when {
                    old == null -> changes += "add field ${proposed.name}.${field.name}"
                    old.type != field.type -> changes += "update field ${proposed.name}.${field.name}: ${old.type} -> ${field.type}"
                    old.defaultExpression != field.defaultExpression -> changes += "preserve field default ${proposed.name}.${field.name}"
                }
            }
            existing.fields
                .filterNot { it.name in proposedFields }
                .forEach { field -> changes += "remove field ${proposed.name}.${field.name}" }
        }

        val existingLines = existingContent.replace("\r\n", "\n").replace("\r", "\n").lines()
        val proposedImports = requiredImports(proposedClasses.values.toList())
        proposedImports
            .filterNot { wanted -> existingLines.any { it.trim() == wanted } }
            .forEach { changes += "add import $it" }

        return changes
            .filterNot { it.startsWith("preserve field default ") }
            .distinct()
    }

    private fun renderFreshModule(classes: List<ModelSpec>): String =
        buildString {
            classes.forEachIndexed { index, spec ->
                if (index > 0) appendLine()
                append(renderClass(spec, preservedBody = emptyList()))
            }
        }.trimEnd()

    private fun mergeIntoExisting(existingContent: String, classes: List<ModelSpec>): String {
        val normalized = existingContent.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.lines().let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        val astBlocks = astClassBlocks(normalized).associateBy { it.start }
        val byName = classes.associateBy { it.name }
        val handled = mutableSetOf<String>()
        val out = mutableListOf<String>()
        var index = 0

        while (index < lines.size) {
            val block = astBlocks[index] ?: classBlockAt(lines, index)
            if (block == null) {
                out += lines[index]
                index += 1
                continue
            }
            val spec = byName[block.name]
            if (spec == null) {
                out += lines.subList(block.start, block.endExclusive)
            } else {
                val mergedSpec = mergeExistingFieldDefaults(spec, block.fields.orEmpty())
                val pending = classes.filterNot { it.name in handled || it.name == spec.name }
                val ownedInsertions = pending.filter { insertion -> mergedSpec.referencesType(insertion.name) }
                ownedInsertions.forEach { insertion ->
                    if (out.isNotEmpty() && out.last().isNotBlank()) out += ""
                    out += renderClass(insertion, preservedBody = emptyList()).lines()
                    handled += insertion.name
                    out += ""
                }
                handled += spec.name
                out += renderClass(mergedSpec, preservedBody = preservedClassBody(lines.subList(block.classLine + 1, block.endExclusive))).lines()
            }
            index = block.endExclusive
        }

        classes.filterNot { it.name in handled }.forEach { spec ->
            trimTrailingBlankLines(out)
            if (out.isNotEmpty()) out += ""
            out += renderClass(spec, preservedBody = emptyList()).lines()
        }

        return out.joinToString("\n")
    }

    private fun renderClass(spec: ModelSpec, preservedBody: List<String>): String =
        buildString {
            appendLine("@dataclass")
            appendLine("class ${spec.name}:")
            if (spec.fields.isEmpty() && preservedBody.isEmpty()) {
                appendLine("    pass")
                return@buildString
            }
            spec.fields.forEach { field ->
                val defaultSuffix = field.defaultExpression?.let { " = $it" }.orEmpty()
                appendLine("    ${field.name}: ${field.type}$defaultSuffix")
            }
            if (preservedBody.isNotEmpty()) {
                if (spec.fields.isNotEmpty()) appendLine()
                preservedBody.forEach { appendLine(it) }
            }
        }.trimEnd()

    private fun mergeExistingFieldDefaults(classes: List<ModelSpec>, existingContent: String): List<ModelSpec> {
        if (existingContent.isBlank()) return classes
        val existingByClass = modelSpecsInContent(existingContent)
        return classes.map { spec ->
            mergeExistingFieldDefaults(spec, existingByClass[spec.name]?.fields.orEmpty())
        }
    }

    private fun mergeExistingFieldDefaults(spec: ModelSpec, existingFields: List<ModelField>): ModelSpec {
        if (existingFields.isEmpty()) return spec
        val existingByName = existingFields.associateBy { it.name }
        return spec.copy(
            fields = spec.fields.map { field ->
                val existing = existingByName[field.name] ?: return@map backwardsCompatibleNewField(field)
                if (existing.type != field.type) return@map field
                field.copy(defaultExpression = existing.defaultExpression)
            },
        )
    }

    private fun backwardsCompatibleNewField(field: ModelField): ModelField {
        if (field.defaultExpression != null) return field
        if (field.type.contains("Optional[") || field.type.contains("| None")) return field
        val referencesModelType = typeIdentifiers(field.type).any {
            it.firstOrNull()?.isUpperCase() == true && it !in nonModelReferenceTypeNames
        }
        if (!referencesModelType) return field
        return field.copy(type = "Optional[${field.type}]", defaultExpression = "None")
    }

    private fun preservedClassBody(bodyLines: List<String>): List<String> {
        val kept = bodyLines.filterNot { line ->
            val trimmed = line.trim()
            trimmed == "pass" || TOP_LEVEL_FIELD.matches(line)
        }.toMutableList()
        trimLeadingBlankLines(kept)
        trimTrailingBlankLines(kept)
        return kept
    }

    private fun classBlockAt(lines: List<String>, index: Int): ClassBlock? {
        var classLine = index
        var classMatch = CLASS_HEADER.find(lines[classLine])
        if (classMatch == null && isTopLevelDecorator(lines[index])) {
            classLine = index
            while (classLine < lines.size && isTopLevelDecorator(lines[classLine])) classLine += 1
            classMatch = lines.getOrNull(classLine)?.let(CLASS_HEADER::find)
        }
        classMatch ?: return null

        var start = classLine
        while (start > 0 && isTopLevelDecorator(lines[start - 1])) {
            start -= 1
        }
        var end = classLine + 1
        while (end < lines.size) {
            val line = lines[end]
            if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t")) {
                if (!line.trimStart().startsWith("@")) break
                val next = lines.getOrNull(end + 1)
                if (next != null && CLASS_HEADER.find(next) != null) break
            }
            end += 1
        }
        return ClassBlock(
            name = classMatch.groupValues[1],
            start = start,
            classLine = classLine,
            endExclusive = end,
        )
    }

    private fun isTopLevelDecorator(line: String): Boolean =
        line.startsWith("@")

    private fun ensureImports(content: String, imports: List<String>): String {
        val lines = content.lines().toMutableList()
        val dataclassImport = lines.indexOfFirst { it.trim() == "from dataclasses import dataclass" }
        if ("from dataclasses import dataclass, field" in imports && dataclassImport >= 0) {
            lines[dataclassImport] = "from dataclasses import dataclass, field"
        }
        val missing = imports.filterNot { wanted -> lines.any { it.trim() == wanted } }
        if (missing.isEmpty()) return lines.joinToString("\n")
        val insertAt = importInsertionIndex(lines)
        lines.addAll(insertAt, missing + "")
        return lines.joinToString("\n")
    }

    private fun importInsertionIndex(lines: List<String>): Int {
        var index = 0
        if (lines.firstOrNull()?.startsWith("#!") == true) index += 1
        if (lines.getOrNull(index)?.startsWith("\"\"\"") == true || lines.getOrNull(index)?.startsWith("'''") == true) {
            val quote = lines[index].take(3)
            index += 1
            while (index < lines.size && !lines[index].contains(quote)) index += 1
            if (index < lines.size) index += 1
        }
        while (index < lines.size && lines[index].startsWith("from __future__ import ")) index += 1
        return index
    }

    private fun requiredImports(classes: List<ModelSpec>): List<String> {
        val typeNames = classes.flatMap { spec -> spec.fields.flatMap { typeIdentifiers(it.type) } }.toSet()
        val defaultNames = classes.flatMap { spec -> spec.fields.flatMap { defaultIdentifiers(it.defaultExpression) } }.toSet()
        val imports = mutableListOf("from dataclasses import dataclass")
        if ("field" in defaultNames) imports[0] = "from dataclasses import dataclass, field"
        val datetimeNames = listOf("date", "datetime", "time", "timedelta").filter { it in typeNames }
        if (datetimeNames.isNotEmpty()) imports += "from datetime import ${datetimeNames.sorted().joinToString(", ")}"
        if ("Decimal" in typeNames) imports += "from decimal import Decimal"
        if ("UUID" in typeNames) imports += "from uuid import UUID"
        val typingNames = listOf("Any", "Dict", "List", "Optional", "Set", "Tuple", "Union").filter { it in typeNames }
        if (typingNames.isNotEmpty()) imports += "from typing import ${typingNames.sorted().joinToString(", ")}"
        return imports
    }

    private fun typeIdentifiers(raw: String): List<String> =
        Regex("""[A-Za-z_][A-Za-z0-9_]*""")
            .findAll(raw.removeSurrounding("\"").removeSurrounding("'"))
            .map { it.value }
            .filterNot { it in builtinTypeNames }
            .toList()

    private fun orderedModelOutputs(outputs: List<NodeContract>): List<ModelSpec> {
        val parsed = outputs
            .filter { it.name.matches(IDENTIFIER) }
            .associate { output ->
                output.name to ModelSpec(
                    name = output.name,
                    fields = output.schema.lines().mapNotNull(::parseFieldLine),
                )
            }
        val names = parsed.keys
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val ordered = mutableListOf<String>()

        fun visit(name: String) {
            if (name in visited || name in visiting) return
            visiting += name
            parsed[name]?.fields.orEmpty()
                .flatMap { typeIdentifiers(it.type) }
                .filter { it in names }
                .sorted()
                .forEach(::visit)
            visiting -= name
            visited += name
            ordered += name
        }

        names.sorted().forEach(::visit)
        return ordered.map { parsed.getValue(it) }
    }

    private fun ModelSpec.referencesType(typeName: String): Boolean =
        fields.any { field -> typeIdentifiers(field.type).contains(typeName) }

    private fun modelSpecsInContent(content: String): Map<String, ModelSpec> {
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.lines().let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        val astBlocks = astClassBlocks(normalized).associateBy { it.start }
        val specs = mutableMapOf<String, ModelSpec>()
        var index = 0
        while (index < lines.size) {
            val block = astBlocks[index] ?: classBlockAt(lines, index)
            if (block == null) {
                index += 1
                continue
            }
            specs[block.name] = ModelSpec(
                name = block.name,
                fields = block.fields ?: lines.subList(block.classLine + 1, block.endExclusive).mapNotNull(::parseFieldLine),
            )
            index = block.endExclusive
        }
        return specs
    }

    private fun astClassBlocks(content: String): List<ClassBlock> {
        if (content.isBlank()) return emptyList()
        val python = findPythonExecutable() ?: return emptyList()
        val lines = content.lines().let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        return try {
            val process = ProcessBuilder(python, "-c", AST_CLASS_SCRIPT)
                .redirectErrorStream(true)
                .start()
            OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8).use { writer ->
                gson.toJson(AstClassRequest(content), writer)
            }
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return emptyList()
            }
            if (process.exitValue() != 0) return emptyList()
            val stdout = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            val output = gson.fromJson(stdout, AstClassOutput::class.java)
            output.classes
                .filter { it.name.matches(IDENTIFIER) && it.classLine > 0 && it.endExclusive > it.classLine }
                .map { cls ->
                    ClassBlock(
                        name = cls.name,
                        start = (cls.start - 1).coerceAtLeast(0),
                        classLine = (cls.classLine - 1).coerceAtLeast(0),
                        endExclusive = cls.endExclusive.coerceAtMost(lines.size).coerceAtLeast((cls.classLine - 1).coerceAtLeast(0) + 1),
                        fields = cls.fields
                            .filter { it.name.matches(IDENTIFIER) && it.type.isNotBlank() }
                            .map { ModelField(it.name, it.type, it.defaultExpression) },
                    )
                }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun findPythonExecutable(): String? {
        val candidates = listOf("python3", "python")
        return candidates.firstOrNull { exe ->
            try {
                val process = ProcessBuilder(exe, "--version").redirectErrorStream(true).start()
                process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun parseFieldLine(line: String): ModelField? {
        val cleaned = line
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
        val name = cleaned.substringBefore(":", "").trim()
        val typePart = cleaned.substringAfter(":", "").trim()
        if (!name.matches(IDENTIFIER)) return null
        if (typePart.isBlank()) return null
        val (type, defaultExpression) = splitTypeAndDefault(typePart)
        return ModelField(name, type, defaultExpression)
    }

    private fun splitTypeAndDefault(typePart: String): Pair<String, String?> {
        val equalsIndex = typePart.indexOf('=')
        if (equalsIndex < 0) return typePart.trim() to null
        val type = typePart.substring(0, equalsIndex).trim()
        val defaultExpression = typePart.substring(equalsIndex + 1).trim().takeIf { it.isNotEmpty() }
        return type to defaultExpression
    }

    private fun defaultIdentifiers(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return Regex("""[A-Za-z_][A-Za-z0-9_]*""")
            .findAll(raw)
            .map { it.value }
            .filterNot { it in builtinTypeNames }
            .toList()
    }

    private fun trimLeadingBlankLines(lines: MutableList<String>) {
        while (lines.firstOrNull()?.isBlank() == true) lines.removeAt(0)
    }

    private fun trimTrailingBlankLines(lines: MutableList<String>) {
        while (lines.lastOrNull()?.isBlank() == true) lines.removeAt(lines.lastIndex)
    }

    data class ModelSpec(val name: String, val fields: List<ModelField>)
    data class ModelField(val name: String, val type: String, val defaultExpression: String? = null)
    data class RenderResult(val content: String, val changes: List<String>)

    private data class ClassBlock(
        val name: String,
        val start: Int,
        val classLine: Int,
        val endExclusive: Int,
        val fields: List<ModelField>? = null,
    )

    private data class AstClassRequest(val source: String)
    private data class AstClassOutput(val classes: List<AstClass> = emptyList())
    private data class AstClass(
        val name: String = "",
        val start: Int = 1,
        val classLine: Int = 1,
        val endExclusive: Int = 1,
        val fields: List<AstField> = emptyList(),
    )
    private data class AstField(
        val name: String = "",
        val type: String = "",
        val defaultExpression: String? = null,
    )

    private val IDENTIFIER = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
    private val CLASS_HEADER = Regex("""^class\s+([A-Za-z_][A-Za-z0-9_]*)\b.*:\s*$""")
    private val TOP_LEVEL_FIELD = Regex("""^    [A-Za-z_][A-Za-z0-9_]*\s*:\s*[^#=]+(?:=.*)?$""")
    private val builtinTypeNames = setOf(
        "str", "int", "float", "bool", "bytes", "list", "dict", "set", "tuple", "None", "True", "False",
    )
    private val nonModelReferenceTypeNames = setOf(
        "Any", "Decimal", "UUID", "Date", "Datetime", "Time", "Timedelta", "List", "Dict", "Set", "Tuple", "Union",
        "Optional",
    )

    private const val AST_CLASS_SCRIPT = """
import ast
import json
import sys

req = json.load(sys.stdin)
source = req.get("source", "")

def unparse(node, default=""):
    if node is None:
        return default
    try:
        return ast.unparse(node).strip()
    except Exception:
        return default

module = ast.parse(source)
classes = []
for node in module.body:
    if not isinstance(node, ast.ClassDef):
        continue
    start = getattr(node, "lineno", 1)
    for decorator in getattr(node, "decorator_list", []):
        start = min(start, getattr(decorator, "lineno", start))
    fields = []
    for item in node.body:
        if isinstance(item, ast.AnnAssign) and isinstance(item.target, ast.Name):
            fields.append({
                "name": item.target.id,
                "type": unparse(item.annotation, "Any"),
                "defaultExpression": unparse(item.value, None),
            })
    classes.append({
        "name": node.name,
        "start": start,
        "classLine": getattr(node, "lineno", start),
        "endExclusive": getattr(node, "end_lineno", getattr(node, "lineno", start)) + 1,
        "fields": fields,
    })

print(json.dumps({"classes": classes}))
"""
}
