package com.blueprint.service

import com.blueprint.model.AcceptanceCriterion
import com.blueprint.model.AcceptanceCriterionType
import com.blueprint.model.BlueprintNode
import com.blueprint.model.FileScope
import com.blueprint.model.NodeContract
import com.blueprint.model.NodeType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.Locale

/**
 * Lightweight UML/Mermaid/architecture-text importer.
 *
 * This intentionally handles the common demo/product path first:
 * class/entity blocks, simple bullet-field entity blocks, and relationship
 * lines. It creates normal Blueprint nodes instead of introducing a separate
 * graph model, so the existing execution pipeline remains the source of truth.
 */
@Service(Service.Level.PROJECT)
class UmlImportService(private val project: Project) {

    data class ParsedEntity(
        val name: String,
        val fields: List<String>,
    )

    data class ParsedRelationship(
        val from: String,
        val label: String,
        val to: String,
    )

    data class ParsedUml(
        val entities: List<ParsedEntity>,
        val relationships: List<ParsedRelationship>,
        val warnings: List<String>,
    )

    data class ImportResult(
        val nodes: List<BlueprintNode>,
        val parsed: ParsedUml,
        val summary: String,
    )

    fun importNodes(rawText: String): ImportResult {
        val text = rawText.trim()
        val parsed = parse(text)
        if (parsed.entities.isEmpty()) {
            return ImportResult(
                nodes = emptyList(),
                parsed = parsed,
                summary = "No entities found. Try PlantUML/Mermaid class blocks or simple entity bullets.",
            )
        }

        val titleStem = inferTitleStem(parsed.entities)
        val slug = slugify(titleStem)
        val packageSlug = slug.replace('-', '_')
        val schemaPath = "blueprint_demo/imported_$packageSlug/models.py"
        val apiPath = "blueprint_demo/imported_$packageSlug/service.py"
        val uiPath = "blueprint_demo/imported_$packageSlug/cli.py"
        val testPath = "tests/test_${packageSlug}_flow.py"
        val docsPath = "docs/$slug.md"

        val schema = BlueprintNode(
            type = NodeType.SCHEMA,
            title = "01 Imported UML schema contract",
            summary = "Convert imported UML architecture into the upstream schema contract.",
            description = schemaDescription(text, parsed),
            outputs = parsed.entities.map { entity ->
                NodeContract(
                    name = entity.name,
                    kind = "schema",
                    description = "Imported UML entity with ${entity.fields.size} field(s).",
                    schema = entity.fields.joinToString("\n"),
                )
            },
            fileScope = FileScope(paths = listOf(schemaPath)),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Generated schema represents all imported entities: ${parsed.entities.joinToString(", ") { it.name }}."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "Generated schema preserves imported fields and relationship intent."),
                criterion("AC3", AcceptanceCriterionType.CODEGEN, "Generated changes stay inside the schema node file scope.")
            ),
            metadata = mutableMapOf(
                "source" to "uml_import",
                "entityCount" to parsed.entities.size.toString(),
                "relationshipCount" to parsed.relationships.size.toString(),
            )
        )
        val backend = BlueprintNode(
            type = NodeType.BACKEND,
            title = "02 Python service from schema",
            summary = "Create Python service behavior from the imported UML schema contract.",
            description = "Implement Python service behavior that respects the imported schema entities, fields, and relationships.",
            inputs = schema.outputs,
            dependencies = listOf(schema.id),
            fileScope = FileScope(paths = listOf(apiPath)),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Python service uses the imported schema contract from the schema node."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "Service behavior reflects the imported relationships."),
                criterion("AC3", AcceptanceCriterionType.CODEGEN, "Implementation stays inside the declared service file scope.")
            ),
            metadata = mutableMapOf("source" to "uml_import")
        )
        val frontend = BlueprintNode(
            type = NodeType.FRONTEND,
            title = "03 Python CLI for imported model",
            summary = "Create a Python CLI surface for the imported architecture model.",
            description = "Add a simple Python CLI that presents and edits the primary imported entities using the service layer.",
            dependencies = listOf(backend.id),
            fileScope = FileScope(paths = listOf(uiPath)),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.UX, "CLI exposes the primary imported entities and their important fields."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "CLI uses the Python service produced from the imported schema.")
            ),
            metadata = mutableMapOf("source" to "uml_import")
        )
        val test = BlueprintNode(
            type = NodeType.TEST,
            title = "04 Imported contract tests",
            summary = "Validate schema, service, and CLI behavior from the imported UML contract.",
            description = "Add focused pytest-style tests around the imported entities, relationships, and generated service/CLI behavior.",
            dependencies = listOf(backend.id, frontend.id),
            fileScope = FileScope(paths = listOf(testPath)),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.TEST, "Tests cover key imported entities and relationship behavior."),
                criterion("AC2", AcceptanceCriterionType.TEST, "Tests validate the schema/service/CLI contract path.")
            ),
            metadata = mutableMapOf("source" to "uml_import")
        )
        val docs = BlueprintNode(
            type = NodeType.DOCS,
            title = "05 Imported architecture notes",
            summary = "Document the imported UML architecture and implementation mapping.",
            description = "Document how the imported UML entities and relationships map to Python schema, service, CLI, and tests.",
            dependencies = listOf(backend.id, frontend.id),
            fileScope = FileScope(paths = listOf(docsPath)),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.OTHER, "Docs explain imported entities, relationships, Python files, and validation path.")
            ),
            metadata = mutableMapOf("source" to "uml_import")
        )

        val summary = buildString {
            appendLine("UML import summary")
            appendLine("Project: ${project.name}")
            appendLine("Entities: ${parsed.entities.joinToString(", ") { it.name }}")
            if (parsed.relationships.isNotEmpty()) {
                appendLine("Relationships:")
                parsed.relationships.forEach { appendLine("- ${it.from} ${it.label} ${it.to}") }
            }
            if (parsed.warnings.isNotEmpty()) {
                appendLine("Warnings:")
                parsed.warnings.forEach { appendLine("- $it") }
            }
            appendLine("Created nodes: Python schema, service, CLI, test, docs.")
        }.trim()

        return ImportResult(listOf(schema, backend, frontend, test, docs), parsed, summary)
    }

    fun parse(rawText: String): ParsedUml {
        val lines = rawText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filterNot { it.isIgnoredLine() }

        val blockEntities = parseBlockEntities(lines)
        val simpleEntities = parseSimpleBulletEntities(lines)
        val entities = (blockEntities + simpleEntities)
            .groupBy { it.name }
            .map { (_, group) ->
                ParsedEntity(
                    name = group.first().name,
                    fields = group.flatMap { it.fields }.distinct(),
                )
            }
            .sortedBy { it.name }

        val relationships = parseRelationships(lines)
            .filter { rel -> entities.any { it.name == rel.from } && entities.any { it.name == rel.to } }
            .distinctBy { "${it.from}|${it.label}|${it.to}" }

        val warnings = buildList {
            if (entities.isEmpty()) add("No entity/class blocks were detected.")
            if (relationships.isEmpty()) add("No relationships were detected.")
            entities.filter { it.fields.isEmpty() }.forEach { add("${it.name} has no parsed fields.") }
        }

        return ParsedUml(entities, relationships, warnings)
    }

    private fun parseBlockEntities(lines: List<String>): List<ParsedEntity> {
        val entities = mutableListOf<ParsedEntity>()
        var currentName: String? = null
        var fields = mutableListOf<String>()
        val start = Regex("""^(?:class|entity|interface)?\s*([A-Z][A-Za-z0-9_]*)\s*\{\s*$""")

        fun closeCurrent() {
            val name = currentName ?: return
            entities += ParsedEntity(name, fields.map(::cleanField).filter { it.isNotBlank() }.distinct())
            currentName = null
            fields = mutableListOf()
        }

        for (line in lines) {
            val match = start.matchEntire(line)
            when {
                match != null -> {
                    closeCurrent()
                    currentName = match.groupValues[1]
                }
                line == "}" -> closeCurrent()
                currentName != null -> fields += line
            }
        }
        closeCurrent()
        return entities
    }

    private fun parseSimpleBulletEntities(lines: List<String>): List<ParsedEntity> {
        val entities = mutableListOf<ParsedEntity>()
        var i = 0
        while (i < lines.size) {
            val name = lines[i].takeIf { it.isEntityHeader() }
            if (name == null) {
                i++
                continue
            }
            val fields = mutableListOf<String>()
            var j = i + 1
            while (j < lines.size && lines[j].startsWith("-")) {
                fields += cleanField(lines[j])
                j++
            }
            if (fields.isNotEmpty()) {
                entities += ParsedEntity(name, fields.filter { it.isNotBlank() }.distinct())
                i = j
            } else {
                i++
            }
        }
        return entities
    }

    private fun parseRelationships(lines: List<String>): List<ParsedRelationship> {
        val relationships = mutableListOf<ParsedRelationship>()
        val arrow = Regex("""\b([A-Z][A-Za-z0-9_]*)\b\s+(.*?)\s*(?:[-.o*]+>|<[-.o*]+|--|->|<->)\s*(.*?)\b([A-Z][A-Za-z0-9_]*)\b""")
        val belongsTo = Regex("""\b([A-Z][A-Za-z0-9_]*)\b\s+belongs\s+to\s+\b([A-Z][A-Za-z0-9_]*)\b""", RegexOption.IGNORE_CASE)
        val mayVerb = Regex("""\b([A-Z][A-Za-z0-9_]*)\b\s+may\s+([a-zA-Z]+)\s+\b([A-Z][A-Za-z0-9_]*)\b""")

        for (line in lines) {
            val arrowMatch = arrow.find(line)
            if (arrowMatch != null) {
                val from = arrowMatch.groupValues[1]
                val to = arrowMatch.groupValues[4]
                val label = (arrowMatch.groupValues[2] + " " + arrowMatch.groupValues[3]).trim()
                    .ifBlank { "relates to" }
                if (from != to) relationships += ParsedRelationship(from, label, to)
                continue
            }
            val belongs = belongsTo.find(line)
            if (belongs != null) {
                relationships += ParsedRelationship(belongs.groupValues[1], "belongs to", belongs.groupValues[2])
                continue
            }
            val may = mayVerb.find(line)
            if (may != null) {
                relationships += ParsedRelationship(may.groupValues[1], "may ${may.groupValues[2]}", may.groupValues[3])
            }
        }
        return relationships
    }

    private fun schemaDescription(source: String, parsed: ParsedUml): String =
        buildString {
            appendLine("Imported UML / architecture source:")
            appendLine()
            appendLine(source)
            appendLine()
            appendLine("Parsed entities:")
            parsed.entities.forEach { entity ->
                appendLine("- ${entity.name}: ${entity.fields.joinToString(", ").ifBlank { "(no fields parsed)" }}")
            }
            if (parsed.relationships.isNotEmpty()) {
                appendLine()
                appendLine("Parsed relationships:")
                parsed.relationships.forEach { appendLine("- ${it.from} ${it.label} ${it.to}") }
            }
        }.trim()

    private fun inferTitleStem(entities: List<ParsedEntity>): String {
        val preferred = entities.firstOrNull { it.name.contains("Invite", ignoreCase = true) }
            ?: entities.first()
        return preferred.name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .trim()
            .lowercase(Locale.US)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    private fun slugify(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "uml-import" }

    private fun cleanField(raw: String): String =
        raw.trim()
            .removePrefix("-")
            .removePrefix("+")
            .removePrefix("#")
            .removePrefix("~")
            .substringBefore("//")
            .substringBefore("'")
            .trim()

    private fun criterion(id: String, type: AcceptanceCriterionType, description: String): AcceptanceCriterion =
        AcceptanceCriterion(
            id = id,
            type = type,
            description = description,
            verifyWith = "Review generated patch and compare against imported UML contract",
        )

    private fun String.isIgnoredLine(): Boolean {
        if (isBlank()) return false
        val lower = lowercase(Locale.US)
        return lower in setOf("@startuml", "@enduml", "classdiagram", "erdiagram") ||
            startsWith("%%") ||
            startsWith("//")
    }

    private fun String.isEntityHeader(): Boolean {
        if (!matches(Regex("""[A-Z][A-Za-z0-9_ ]{0,60}"""))) return false
        val lower = lowercase(Locale.US)
        return lower !in setOf("relationships", "relationship", "entities", "entity", "classes", "class")
    }
}
