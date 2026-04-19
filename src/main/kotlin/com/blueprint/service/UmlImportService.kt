package com.blueprint.service

import com.blueprint.ir.ArchitectureIR
import com.blueprint.ir.Component
import com.blueprint.ir.ComponentKind
import com.blueprint.ir.DataType
import com.blueprint.ir.Field
import com.blueprint.ir.IRStore
import com.blueprint.ir.IRToNodesCompiler
import com.blueprint.ir.Ownership
import com.blueprint.ir.SourceRef
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

        val irStore = project.service<IRStore>()
        val previousCodeIr = irStore.load()
        val importedIr = project.service<UmlIRImporter>().toIR(parsed)
        val remappedIr = CodeBackedUmlSourceMapper.remap(importedIr, parsed, previousCodeIr)
        val executionIr = CodeBackedUmlSourceMapper.changedModelSubset(remappedIr, previousCodeIr)
        irStore.save(remappedIr)
        val compiled = project.service<IRToNodesCompiler>().compile(executionIr)
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
        if (modelNodes.isEmpty()) return compiledNodes

        val modelNodesByPath = modelNodes
            .mapNotNull { node -> node.fileScope.paths.firstOrNull()?.let { it to node } }
            .groupBy({ it.first }, { it.second })
        if (modelNodesByPath.isEmpty()) return compiledNodes

        val aggregateByPath = modelNodesByPath.mapValues { (sharedModelPath, nodesForPath) ->
            val aggregateOutputs = nodesForPath
                .flatMap { it.outputs }
                .filter { it.kind == "schema" && it.name.isNotBlank() }
                .groupBy { it.name }
                .map { (_, contracts) ->
                    contracts.maxWith(
                        compareBy<NodeContract> { schemaFieldCount(it.schema) }
                            .thenBy { it.schema.length }
                    )
                }
                .sortedBy { it.name }
            val aggregateId = stableNodeId("uml.aggregate.models.$sharedModelPath.${aggregateOutputs.joinToString(",") { it.name }}")
            BlueprintNode(
                id = aggregateId,
                type = NodeType.SCHEMA,
                title = nodesForPath.minByOrNull { it.title }?.title?.replaceAfter(" ", "UML models") ?: "01 UML models",
                summary = "Generate model changes for $sharedModelPath from the current UML.",
                description = buildString {
                    appendLine("Generate coherent Python dataclass changes for the UML models owned by $sharedModelPath.")
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
                    appendLine("Important: this node owns only the UML model classes mapped to $sharedModelPath.")
                }.trim(),
                outputs = aggregateOutputs,
                fileScope = FileScope(paths = listOf(sharedModelPath)),
                acceptanceCriteria = listOf(
                    AcceptanceCriterion(
                        id = "AC1",
                        type = AcceptanceCriterionType.INTERFACE_CONTRACT,
                        description = "Generated models represent mapped UML entities: ${aggregateOutputs.joinToString(", ") { it.name }}.",
                        verifyWith = "Review the proposed patch against the UML.",
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
                        description = "Changes stay inside $sharedModelPath.",
                        verifyWith = "Review changed files.",
                    ),
                ),
                metadata = mutableMapOf(
                    "source" to "uml_aggregate",
                    "componentKind" to "model_group",
                    "entityCount" to aggregateOutputs.size.toString(),
                ),
            )
        }
        val modelNodeIdsByPath = modelNodesByPath.mapValues { (_, nodesForPath) -> nodesForPath.map { it.id }.toSet() }
        val replacementByModelNodeId = modelNodeIdsByPath.flatMap { (path, ids) ->
            ids.map { it to aggregateByPath.getValue(path).id }
        }.toMap()
        val modelNodeIds = replacementByModelNodeId.keys
        val aggregates = aggregateByPath.values.sortedBy { it.fileScope.paths.firstOrNull() ?: "" }

        val remapped = compiledNodes
            .filterNot { it.id in modelNodeIds }
            .map { node ->
                node.copy(
                    dependencies = node.dependencies.map { dep -> replacementByModelNodeId[dep] ?: dep }.distinct()
                )
            }
        return aggregates + remapped
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

internal object CodeBackedUmlSourceMapper {
    fun remap(
        ir: ArchitectureIR,
        parsed: UmlImportService.ParsedUml,
        previousIr: ArchitectureIR?,
    ): ArchitectureIR {
        val previousByName = previousCodeBackedModelsByName(previousIr)
        if (previousByName.isEmpty()) return ir

        val parsedByName = parsed.entities.associateBy { it.name }
        val components = ir.components.map { component ->
            if (component.kind != ComponentKind.MODEL) return@map component
            val path = preferredPathFor(component.name, parsedByName, parsed.relationships, previousByName)
                ?: return@map component
            val previous = previousByName[component.name]
            component.copy(
                ownership = Ownership(files = listOf(path)),
                sourceRef = previous?.sourceRef ?: SourceRef(path),
                description = if (previous == null) {
                    "UML entity mapped into existing code file $path."
                } else {
                    component.description
                },
            )
        }

        return ir.copy(
            components = components,
            dataTypes = dataTypesForComponents(ir.dataTypes, components),
        )
    }

    fun changedModelSubset(ir: ArchitectureIR, previousIr: ArchitectureIR?): ArchitectureIR {
        val previousByName = previousCodeBackedModelsByName(previousIr)
        val codeBackedModels = ir.components.filter { it.kind == ComponentKind.MODEL && it.primaryCodeFile() != null }
        if (codeBackedModels.isEmpty()) return ir

        val changedModels = codeBackedModels.filter { component ->
            val previous = previousByName[component.name]
            previous == null || fieldsDiffer(component.fields, previous.fields)
        }
        val changedIds = changedModels.map { it.id }.toSet()

        return ir.copy(
            components = changedModels,
            dataTypes = dataTypesForComponents(ir.dataTypes, changedModels),
            edges = ir.edges.filter { it.from in changedIds && it.to in changedIds },
            modules = emptyList(),
            contracts = emptyList(),
            events = emptyList(),
            extensionPoints = emptyList(),
        )
    }

    private fun preferredPathFor(
        name: String,
        parsedByName: Map<String, UmlImportService.ParsedEntity>,
        relationships: List<UmlImportService.ParsedRelationship>,
        previousByName: Map<String, Component>,
    ): String? {
        previousByName[name]?.primaryCodeFile()?.let { return it }

        relationships.firstNotNullOfOrNull { rel ->
            when (name) {
                rel.from -> previousByName[rel.to]?.primaryCodeFile()
                rel.to -> previousByName[rel.from]?.primaryCodeFile()
                else -> null
            }
        }?.let { return it }

        parsedByName[name]?.fields.orEmpty()
            .asSequence()
            .mapNotNull { fieldTypeName(it) }
            .mapNotNull { previousByName[it]?.primaryCodeFile() }
            .firstOrNull()
            ?.let { return it }

        parsedByName.values
            .asSequence()
            .filter { entity -> entity.name != name }
            .filter { entity -> entity.fields.any { fieldTypeName(it) == name } }
            .mapNotNull { entity -> previousByName[entity.name]?.primaryCodeFile() }
            .firstOrNull()
            ?.let { return it }

        return previousByName.values
            .mapNotNull { it.primaryCodeFile() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    private fun previousCodeBackedModelsByName(ir: ArchitectureIR?): Map<String, Component> {
        ir ?: return emptyMap()
        return ir.components
            .filter { it.kind == ComponentKind.MODEL }
            .groupBy { it.name }
            .mapNotNull { (name, components) ->
                val selected = components
                    .filter { it.primaryCodeFile() != null }
                    .minByOrNull { sourceRank(it.primaryCodeFile().orEmpty()) }
                    ?: return@mapNotNull null
                name to selected
            }
            .toMap()
    }

    private fun dataTypesForComponents(dataTypes: List<DataType>, components: List<Component>): List<DataType> {
        val componentsByName = components.associateBy { it.name }
        val existingDataTypes = dataTypes.associateBy { it.name }
        return components.map { component ->
            existingDataTypes[component.name]?.copy(
                id = "${component.id}.data",
                fields = component.fields,
                sourceRef = component.sourceRef,
            ) ?: DataType(
                id = "${component.id}.data",
                name = component.name,
                fields = component.fields,
                sourceRef = component.sourceRef,
            )
        }.filter { it.name in componentsByName }
    }

    private fun fieldsDiffer(next: List<Field>, previous: List<Field>): Boolean {
        fun normalized(fields: List<Field>): Map<String, String> =
            fields.associate { it.name to normalizeType(it.type.id) }
        return normalized(next) != normalized(previous)
    }

    private fun fieldTypeName(raw: String): String? {
        val type = raw.substringAfter(":", "").trim().ifBlank { return null }
        return Regex("""[A-Z][A-Za-z0-9_]*""").find(type)?.value
    }

    private fun Component.primaryCodeFile(): String? =
        ownership.files.firstOrNull { path ->
            path.isNotBlank() &&
                !path.startsWith("blueprint_demo/imported_") &&
                !path.startsWith("tests/") &&
                !path.contains("/tests/")
        }

    private fun sourceRank(path: String): String =
        when {
            path.startsWith("app/") -> "0:$path"
            path.startsWith("src/") -> "1:$path"
            path.startsWith("blueprint_demo/") -> "9:$path"
            else -> "2:$path"
        }

    private fun normalizeType(type: String): String =
        when (type.trim()) {
            "string" -> "str"
            "integer" -> "int"
            "boolean" -> "bool"
            "decimal" -> "Decimal"
            else -> type.trim()
        }
}
