package com.blueprint.ir

import com.blueprint.service.UmlImportService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.Locale

/**
 * Lifts a parsed UML document into an ArchitectureIR.
 *
 * Each parsed entity becomes a MODEL component (the declared schema). On top
 * of that, v1 emits a small set of scaffolding components — a service, a CLI,
 * a test, a docs component — all owned under a package slug derived from the
 * primary entity. Relationships become IR edges.
 *
 * This replaces the hardcoded 5-node output of UmlImportService with an
 * IR-driven shape that scales with the number of entities.
 */
@Service(Service.Level.PROJECT)
class UmlIRImporter(private val project: Project) {

    fun toIR(parsed: UmlImportService.ParsedUml): ArchitectureIR {
        if (parsed.entities.isEmpty()) {
            return ArchitectureIR(project = ProjectMeta(name = project.name))
        }

        val titleStem = inferTitleStem(parsed.entities)
        val slug = slugify(titleStem)
        val packageSlug = slug.replace('-', '_')
        val pkg = "blueprint_demo/imported_$packageSlug"

        val modelFile = "$pkg/models.py"
        val serviceFile = "$pkg/service.py"
        val cliFile = "$pkg/cli.py"
        val testFile = "tests/test_${packageSlug}_flow.py"
        val docsFile = "docs/$slug.md"

        // One MODEL component per parsed entity. Packed into models.py so the
        // compiled node's file scope is clean.
        val modelComponents = parsed.entities.map { entity ->
            val id = "imported.$packageSlug.models.${entity.name.lowercase()}"
            Component(
                id = id,
                name = entity.name,
                kind = ComponentKind.MODEL,
                concurrency = Concurrency.SYNC,
                ownership = Ownership(files = listOf(modelFile)),
                fields = entity.fields.mapNotNull { raw -> fieldFromBullet(raw) },
                tags = setOf("dataclass"),
                description = "Imported UML entity (${entity.fields.size} field(s))",
            )
        }

        val dataTypes = modelComponents.map { comp ->
            DataType(
                id = "${comp.id}.data",
                name = comp.name,
                kind = DataKind.DATACLASS,
                fields = comp.fields,
            )
        }

        // Service scaffolding: depends on all models.
        val serviceId = "imported.$packageSlug.service"
        val serviceComponent = Component(
            id = serviceId,
            name = "${titleStem.titleCase()}Service",
            kind = ComponentKind.SERVICE,
            ownership = Ownership(files = listOf(serviceFile)),
            requires = modelComponents.map { it.id },
            description = "Service behavior for imported ${parsed.entities.joinToString(", ") { it.name }}.",
            tags = setOf("scaffold"),
        )

        val cliId = "imported.$packageSlug.cli"
        val cliComponent = Component(
            id = cliId,
            name = "${titleStem.titleCase()}CLI",
            kind = ComponentKind.CLI,
            ownership = Ownership(files = listOf(cliFile)),
            requires = listOf(serviceId),
            description = "CLI surface for the imported model.",
            tags = setOf("scaffold"),
        )

        val testId = "imported.$packageSlug.tests"
        val testComponent = Component(
            id = testId,
            name = "${titleStem.titleCase()}FlowTests",
            kind = ComponentKind.TEST,
            ownership = Ownership(files = listOf(testFile)),
            requires = listOf(serviceId, cliId),
            description = "Contract tests for the imported model flow.",
            tags = setOf("scaffold"),
        )

        val docsId = "imported.$packageSlug.docs"
        val docsComponent = Component(
            id = docsId,
            name = "${titleStem.titleCase()}Notes",
            kind = ComponentKind.DOCS,
            ownership = Ownership(files = listOf(docsFile)),
            requires = listOf(serviceId, cliId),
            description = "Architecture notes for the imported model.",
            tags = setOf("scaffold"),
        )

        // Relationship edges between models.
        val modelByName = modelComponents.associateBy { it.name }
        val relationshipEdges = parsed.relationships
            .mapNotNull { rel ->
                val fromId = modelByName[rel.from]?.id ?: return@mapNotNull null
                val toId = modelByName[rel.to]?.id ?: return@mapNotNull null
                val kind = when {
                    rel.label.contains("extends", ignoreCase = true) -> EdgeKind.EXTENDS
                    rel.label.contains("contains", ignoreCase = true) ||
                        rel.label.contains("belongs", ignoreCase = true) -> EdgeKind.CONTAINS
                    else -> EdgeKind.REFERENCES
                }
                Edge(fromId, toId, EdgeTargetKind.COMPONENT, kind, rel.label)
            }
            .distinct()

        // Scaffolding dependency edges (service depends on models, etc.).
        val scaffoldEdges = buildList {
            modelComponents.forEach { add(Edge(serviceId, it.id, EdgeTargetKind.COMPONENT, EdgeKind.REFERENCES, "uses")) }
            add(Edge(cliId, serviceId, EdgeTargetKind.COMPONENT, EdgeKind.CALLS, "invokes"))
            add(Edge(testId, serviceId, EdgeTargetKind.COMPONENT, EdgeKind.CALLS, "tests"))
            add(Edge(testId, cliId, EdgeTargetKind.COMPONENT, EdgeKind.CALLS, "tests"))
            add(Edge(docsId, serviceId, EdgeTargetKind.COMPONENT, EdgeKind.REFERENCES, "documents"))
            add(Edge(docsId, cliId, EdgeTargetKind.COMPONENT, EdgeKind.REFERENCES, "documents"))
        }

        val components = modelComponents + listOf(serviceComponent, cliComponent, testComponent, docsComponent)

        return ArchitectureIR(
            project = ProjectMeta(name = project.name),
            components = components,
            contracts = emptyList(),
            dataTypes = dataTypes,
            edges = (relationshipEdges + scaffoldEdges).distinct(),
            coverage = RecoveryCoverage(
                componentsRecovered = components.size,
                componentsTotal = components.size,
                contractsRecovered = 0,
            ),
        )
    }

    private fun fieldFromBullet(raw: String): Field? {
        val cleaned = raw.trim().removePrefix("-").removePrefix("+").removePrefix("#").removePrefix("~").trim()
        if (cleaned.isBlank()) return null
        // Accept "name: Type", "name Type", or bare "name".
        val (name, type) = when {
            ":" in cleaned -> cleaned.substringBefore(":").trim() to cleaned.substringAfter(":").trim()
            else -> {
                val parts = cleaned.split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else parts[0] to "Any"
            }
        }
        if (name.isBlank() || !name.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*"""))) return null
        return Field(name, TypeRef(type.substringBefore(" ").ifBlank { "Any" }))
    }

    private fun inferTitleStem(entities: List<UmlImportService.ParsedEntity>): String {
        val preferred = entities.firstOrNull { it.name.contains("Invite", ignoreCase = true) }
            ?: entities.first()
        return preferred.name
    }

    private fun slugify(value: String): String =
        value.replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "uml-import" }

    private fun String.titleCase(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}
