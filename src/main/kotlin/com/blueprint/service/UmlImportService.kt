package com.blueprint.service

import com.blueprint.ir.IRStore
import com.blueprint.ir.IRToNodesCompiler
import com.blueprint.ir.UmlIRImporter
import com.blueprint.model.AcceptanceCriterion
import com.blueprint.model.AcceptanceCriterionType
import com.blueprint.model.BlueprintNode
import com.blueprint.model.FileScope
import com.blueprint.model.NodeContract
import com.blueprint.model.NodeType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/**
 * UML / Mermaid / architecture-text importer.
 *
 * parse() still produces the lightweight ParsedUml that the UI uses for
 * live previews. importNodes() now routes through the architecture IR:
 *
 *   raw text ─► parse ─► UmlIRImporter.toIR ─► IRStore.save ─► IRToNodesCompiler.compile
 *
 * The returned ImportResult shape is preserved so BlueprintPanel continues
 * to compile. The number of emitted nodes is no longer fixed at 5 — it
 * scales with the components the IR produces.
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

        val ir = project.service<UmlIRImporter>().toIR(parsed)
        project.service<IRStore>().save(ir)
        val compiled = project.service<IRToNodesCompiler>().compile(ir)
        val nodes = aggregateSharedModelNodes(parsed, compiled.nodes)

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
            if (compiled.warnings.isNotEmpty()) {
                compiled.warnings.forEach { appendLine("- $it") }
            }
            val nodeLine = nodes.joinToString(", ") { node ->
                "${node.title} [${node.metadata["componentKind"] ?: node.type.name.lowercase()}]"
            }
            appendLine("Created ${nodes.size} node(s): $nodeLine")
        }.trim()

        return ImportResult(nodes, parsed, summary)
    }

    private fun aggregateSharedModelNodes(
        parsed: ParsedUml,
        compiledNodes: List<BlueprintNode>,
    ): List<BlueprintNode> {
        val modelNodes = compiledNodes.filter {
            it.type == NodeType.SCHEMA && it.metadata["componentKind"] == "model"
        }
        if (modelNodes.size <= 1) return compiledNodes

        val sharedModelPath = modelNodes
            .flatMap { it.fileScope.paths }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: return compiledNodes

        val modelNodeIds = modelNodes.map { it.id }.toSet()
        val aggregateId = stableNodeId("uml.aggregate.models.$sharedModelPath.${parsed.entities.joinToString(",") { it.name }}")
        val aggregateOutputs = (modelNodes.flatMap { it.outputs } + compiledNodes.flatMap { it.inputs })
            .filter { it.kind == "schema" && it.name.isNotBlank() }
            .groupBy { it.name }
            .map { (_, contracts) ->
                contracts.maxWith(
                    compareBy<NodeContract> { schemaFieldCount(it.schema) }
                        .thenBy { it.schema.length }
                )
            }
            .sortedBy { it.name }
        val aggregate = BlueprintNode(
            id = aggregateId,
            type = NodeType.SCHEMA,
            title = "01 UML models",
            summary = "Generate the shared models file from the current UML.",
            description = buildString {
                appendLine("Generate one coherent Python models module from the full UML.")
                appendLine()
                appendLine("Entities:")
                aggregateOutputs.forEach { output ->
                    val fields = output.schema.lines().filter { it.isNotBlank() }.joinToString(", ")
                    appendLine("- ${output.name}: ${fields.ifBlank { "(no fields)" }}")
                }
                if (parsed.relationships.isNotEmpty()) {
                    appendLine()
                    appendLine("Relationships:")
                    parsed.relationships.forEach { rel -> appendLine("- ${rel.from} ${rel.label} ${rel.to}") }
                }
                appendLine()
                appendLine("Important: this node owns all UML model classes in $sharedModelPath. It may create or update sibling model classes together.")
            }.trim(),
            outputs = aggregateOutputs,
            fileScope = FileScope(paths = listOf(sharedModelPath)),
            acceptanceCriteria = listOf(
                AcceptanceCriterion(
                    id = "AC1",
                    type = AcceptanceCriterionType.INTERFACE_CONTRACT,
                    description = "Generated models represent every UML entity: ${aggregateOutputs.joinToString(", ") { it.name }}.",
                    verifyWith = "Review the proposed models.py patch against the UML.",
                ),
                AcceptanceCriterion(
                    id = "AC2",
                    type = AcceptanceCriterionType.INTERFACE_CONTRACT,
                    description = "Generated models preserve declared fields and relationship intent from the UML.",
                    verifyWith = "Review class fields and references in the proposed patch.",
                ),
                AcceptanceCriterion(
                    id = "AC3",
                    type = AcceptanceCriterionType.CODEGEN,
                    description = "Changes stay inside the shared models file scope.",
                    verifyWith = "Review changed files.",
                ),
            ),
            metadata = mutableMapOf(
                "source" to "uml_aggregate",
                "componentKind" to "model_group",
                "entityCount" to aggregateOutputs.size.toString(),
            ),
        )

        val remapped = compiledNodes
            .filterNot { it.id in modelNodeIds }
            .map { node ->
                node.copy(
                    dependencies = node.dependencies.map { dep -> if (dep in modelNodeIds) aggregateId else dep }.distinct()
                )
            }
        return listOf(aggregate) + remapped
    }

    private fun stableNodeId(seed: String): String =
        UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun schemaFieldCount(schema: String): Int =
        schema.lines().count { line ->
            val name = line.substringBefore(":", "").trim()
            name.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*"""))
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
        val start = Regex("""^(?:class|entity|interface)?\s*([A-Z][A-Za-z0-9_]*)(?:\s+(?:<<[^>]+>>|&lt;&lt;[^&]+&gt;&gt;))*\s*\{\s*$""")

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

    private fun cleanField(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("-")
            .removePrefix("+")
            .removePrefix("#")
            .removePrefix("~")
            .substringBefore("//")
            .substringBefore("'")
            .trim()
        if (cleaned.matches(Regex("""(?:<<[^>]+>>|&lt;&lt;[^&]+&gt;&gt;)"""))) return ""
        return cleaned
    }

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
