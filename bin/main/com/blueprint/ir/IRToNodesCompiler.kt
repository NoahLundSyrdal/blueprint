package com.blueprint.ir

import com.blueprint.model.AcceptanceCriterion
import com.blueprint.model.AcceptanceCriterionType
import com.blueprint.model.BlueprintNode
import com.blueprint.model.FileScope
import com.blueprint.model.NodeContract
import com.blueprint.model.NodeType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.UUID

/**
 * Deterministic projection from ArchitectureIR to BlueprintNode graph.
 *
 * Each component produces exactly one BlueprintNode. Node ids are derived
 * from component ids (name-based UUIDs) so recompiling the same IR yields
 * the same node ids — this is what lets NodeRegistry keep artifacts across
 * IR edits without duplicating rows.
 *
 * The compile logic is pure (no Project dependency) and exposed via the
 * [IRCompilation] object so non-service callers (tests, headless tools)
 * can invoke it directly.
 */
@Service(Service.Level.PROJECT)
class IRToNodesCompiler(@Suppress("UNUSED_PARAMETER") project: Project) {

    data class CompileResult(
        val nodes: List<BlueprintNode>,
        val warnings: List<String>,
    )

    fun compile(ir: ArchitectureIR): CompileResult = IRCompilation.compile(ir)
}

/** Pure, testable projection of IR → BlueprintNode list. */
object IRCompilation {

    fun compile(ir: ArchitectureIR): IRToNodesCompiler.CompileResult {
        if (ir.components.isEmpty()) {
            return IRToNodesCompiler.CompileResult(emptyList(), listOf("IR has no components; nothing to compile."))
        }

        val warnings = mutableListOf<String>()
        val contractsById = ir.contracts.associateBy { it.id }
        val componentsById = ir.components.associateBy { it.id }
        val nodeIdsByComponent: Map<String, String> = ir.components.associate { c -> c.id to stableNodeId(c.id) }

        val orderedComponents = ir.components.sortedWith(
            compareBy<Component> { kindOrder(it.kind) }
                .thenBy { it.ownership.files.firstOrNull() ?: "" }
                .thenBy { it.name }
        )

        val nodes = orderedComponents.mapIndexed { index, component ->
            val title = buildTitle(index + 1, component)
            val outputs = buildOutputs(component, contractsById)
            val inputs = buildInputs(component, componentsById, contractsById)
            val dependencies = resolveDependencies(component, ir, nodeIdsByComponent)

            val fileScope = FileScope(
                paths = component.ownership.files,
                globs = component.ownership.globs,
            ).also {
                if (it.isEmpty()) warnings += "Component ${component.id} has no ownership; node scope is empty."
            }

            BlueprintNode(
                id = nodeIdsByComponent.getValue(component.id),
                type = mapType(component.kind),
                title = title,
                summary = component.description.ifBlank { "${component.kind.label()} component ${component.name}." },
                description = buildDescription(component, ir),
                inputs = inputs,
                outputs = outputs,
                dependencies = dependencies,
                fileScope = fileScope,
                acceptanceCriteria = buildAcceptance(component),
                invariants = component.forbiddenPatterns.map { "must not use $it" },
                riskLevel = if (component.kind == ComponentKind.MODEL) "LOW" else "MEDIUM",
                metadata = mutableMapOf(
                    "source" to "ir",
                    "componentId" to component.id,
                    "componentKind" to component.kind.name.lowercase(),
                    "concurrency" to component.concurrency.name.lowercase(),
                ).apply {
                    if (component.tags.isNotEmpty()) put("tags", component.tags.joinToString(","))
                    component.sourceRef?.let { put("sourcePath", it.path) }
                },
            )
        }

        return IRToNodesCompiler.CompileResult(nodes, warnings)
    }

    private fun buildTitle(index: Int, component: Component): String {
        val prefix = index.toString().padStart(2, '0')
        return "$prefix ${component.name} ${component.kind.label()}"
    }

    private fun buildOutputs(component: Component, contractsById: Map<String, Contract>): List<NodeContract> {
        val fromContracts = component.provides.mapNotNull { contractsById[it] }.map { contract ->
            NodeContract(
                name = contract.name,
                kind = contractKindToString(contract.kind),
                description = contract.description.ifBlank { "Contract ${contract.name} provided by ${component.name}." },
                schema = contract.operations.joinToString("\n") { op -> formatOperation(op) },
            )
        }
        if (fromContracts.isNotEmpty()) return fromContracts

        return when (component.kind) {
            ComponentKind.MODEL -> listOf(
                NodeContract(
                    name = component.name,
                    kind = "schema",
                    description = "Data model ${component.name} with ${component.fields.size} field(s).",
                    schema = component.fields.joinToString("\n") { f -> "${f.name}: ${f.type}" },
                )
            )
            ComponentKind.CLI -> listOf(
                NodeContract(name = component.name, kind = "cli", description = "CLI surface for ${component.name}.")
            )
            ComponentKind.TEST, ComponentKind.DOCS -> emptyList()
            else -> listOf(
                NodeContract(
                    name = component.name,
                    kind = "component",
                    description = "${component.kind.label()} ${component.name}.",
                    schema = component.operations.joinToString("\n") { op -> formatOperation(op) },
                )
            )
        }
    }

    private fun buildInputs(
        component: Component,
        componentsById: Map<String, Component>,
        contractsById: Map<String, Contract>,
    ): List<NodeContract> = component.requires.mapNotNull { id ->
        val contract = contractsById[id]
        if (contract != null) {
            NodeContract(
                name = contract.name,
                kind = contractKindToString(contract.kind),
                description = "Required contract ${contract.name}.",
                schema = contract.operations.joinToString("\n") { op -> formatOperation(op) },
            )
        } else {
            val comp = componentsById[id] ?: componentsById.values.firstOrNull { it.name == id }
            if (comp == null) null
            else NodeContract(
                name = comp.name,
                kind = if (comp.kind == ComponentKind.MODEL) "schema" else "component",
                description = "Requires ${comp.kind.label()} ${comp.name}.",
                schema = comp.fields.joinToString("\n") { f -> "${f.name}: ${f.type}" },
            )
        }
    }

    private fun resolveDependencies(
        component: Component,
        ir: ArchitectureIR,
        nodeIds: Map<String, String>,
    ): List<String> {
        val deps = linkedSetOf<String>()
        val byName = ir.components.associateBy { it.name }
        component.requires.forEach { id ->
            val direct = nodeIds[id]
            if (direct != null) {
                deps += direct
                return@forEach
            }
            val providingComponent = ir.components.firstOrNull { it.provides.contains(id) }
            if (providingComponent != null) {
                nodeIds[providingComponent.id]?.let { deps += it }
                return@forEach
            }
            val byNameMatch = byName[id]
            if (byNameMatch != null) {
                nodeIds[byNameMatch.id]?.let { deps += it }
            }
        }
        ir.edges
            .filter { it.from == component.id && it.toKind == EdgeTargetKind.COMPONENT }
            .filter { it.kind in DEPENDENCY_EDGES }
            .forEach { edge -> nodeIds[edge.to]?.let { deps += it } }
        return deps.toList()
    }

    private fun buildAcceptance(component: Component): List<AcceptanceCriterion> {
        val out = mutableListOf<AcceptanceCriterion>()
        when (component.kind) {
            ComponentKind.MODEL -> {
                out += ac("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT,
                    "Generated model preserves ${component.fields.size} declared field(s) with types.")
                out += ac("AC2", AcceptanceCriterionType.CODEGEN,
                    "Changes stay inside this node's file scope.")
            }
            ComponentKind.PORT -> {
                out += ac("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT,
                    "Generated interface exposes ${component.operations.size} declared operation(s).")
                out += ac("AC2", AcceptanceCriterionType.CODEGEN,
                    "Changes stay inside this node's file scope.")
            }
            ComponentKind.SERVICE, ComponentKind.ADAPTER, ComponentKind.REGISTRY, ComponentKind.JOB -> {
                out += ac("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT,
                    "Implementation fulfils contracts: ${component.provides.ifEmpty { listOf("(component role)") }.joinToString(", ")}.")
                if (component.requires.isNotEmpty()) {
                    out += ac("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT,
                        "Implementation consumes only declared contracts: ${component.requires.joinToString(", ")}.")
                }
                out += ac("AC3", AcceptanceCriterionType.CODEGEN,
                    "Changes stay inside this node's file scope.")
                if (component.concurrency == Concurrency.ASYNC) {
                    out += ac("AC4", AcceptanceCriterionType.NON_FUNCTIONAL,
                        "All public operations use async def and await; no blocking I/O.")
                }
            }
            ComponentKind.CLI -> {
                out += ac("AC1", AcceptanceCriterionType.UX, "CLI exposes the primary operations and respects declared contracts.")
                out += ac("AC2", AcceptanceCriterionType.CODEGEN, "Changes stay inside this node's file scope.")
            }
            ComponentKind.TEST -> {
                out += ac("AC1", AcceptanceCriterionType.TEST, "Tests cover declared contracts and pass under pytest.")
                out += ac("AC2", AcceptanceCriterionType.CODEGEN, "Tests stay inside this node's file scope.")
            }
            ComponentKind.DOCS -> {
                out += ac("AC1", AcceptanceCriterionType.OTHER, "Docs explain the component, its contracts, and its validation path.")
            }
            ComponentKind.OTHER -> {
                out += ac("AC1", AcceptanceCriterionType.CODEGEN, "Changes stay inside this node's file scope.")
            }
        }
        if (component.forbiddenPatterns.isNotEmpty()) {
            out += ac("AC_POLICY", AcceptanceCriterionType.NON_FUNCTIONAL,
                "Implementation avoids forbidden patterns: ${component.forbiddenPatterns.joinToString(", ")}.")
        }
        return out
    }

    private fun buildDescription(component: Component, ir: ArchitectureIR): String = buildString {
        appendLine(component.description.ifBlank { "${component.kind.label()} ${component.name}." })
        appendLine()
        if (component.ownership.files.isNotEmpty() || component.ownership.globs.isNotEmpty()) {
            appendLine("Ownership:")
            component.ownership.files.forEach { appendLine("- file: $it") }
            component.ownership.globs.forEach { appendLine("- glob: $it") }
            appendLine()
        }
        if (component.provides.isNotEmpty()) appendLine("Provides: ${component.provides.joinToString(", ")}")
        if (component.requires.isNotEmpty()) appendLine("Requires: ${component.requires.joinToString(", ")}")
        if (component.concurrency == Concurrency.ASYNC) appendLine("Concurrency: async")
        if (component.opaqueAnnotations.isNotEmpty()) {
            appendLine()
            appendLine("Opaque annotations preserved:")
            component.opaqueAnnotations.forEach { ann -> appendLine("- ${ann.symbol} (${ann.reason})") }
        }
        val incoming = ir.edges.filter { it.to == component.id }
        if (incoming.isNotEmpty()) {
            appendLine()
            appendLine("Referenced by:")
            incoming.take(12).forEach { edge -> appendLine("- ${edge.from} -[${edge.kind.name.lowercase()}]-> ${component.name}") }
        }
    }.trim()

    private fun formatOperation(op: Operation): String {
        val params = op.params.joinToString(", ") { p ->
            val suffix = if (p.default != null) " = ${p.default}" else ""
            "${p.name}: ${p.type}$suffix"
        }
        val prefix = if (op.concurrency == Concurrency.ASYNC) "async " else ""
        return "${prefix}${op.name}($params) -> ${op.returns}"
    }

    private fun contractKindToString(kind: ContractKind): String = when (kind) {
        ContractKind.INTERFACE -> "api"
        ContractKind.EVENT -> "event"
        ContractKind.SCHEMA -> "schema"
        ContractKind.CLI -> "cli"
    }

    private fun ac(id: String, type: AcceptanceCriterionType, description: String): AcceptanceCriterion =
        AcceptanceCriterion(
            id = id,
            type = type,
            description = description,
            verifyWith = "Review generated patch against declared IR component contract",
        )

    private fun mapType(kind: ComponentKind): NodeType = when (kind) {
        ComponentKind.MODEL -> NodeType.SCHEMA
        ComponentKind.PORT -> NodeType.SCHEMA
        ComponentKind.SERVICE -> NodeType.BACKEND
        ComponentKind.ADAPTER -> NodeType.BACKEND
        ComponentKind.REGISTRY -> NodeType.BACKEND
        ComponentKind.JOB -> NodeType.BACKEND
        ComponentKind.CLI -> NodeType.FRONTEND
        ComponentKind.TEST -> NodeType.TEST
        ComponentKind.DOCS -> NodeType.DOCS
        ComponentKind.OTHER -> NodeType.OTHER
    }

    private fun kindOrder(kind: ComponentKind): Int = when (kind) {
        ComponentKind.MODEL -> 0
        ComponentKind.PORT -> 1
        ComponentKind.ADAPTER -> 2
        ComponentKind.REGISTRY -> 3
        ComponentKind.SERVICE -> 4
        ComponentKind.JOB -> 5
        ComponentKind.CLI -> 6
        ComponentKind.TEST -> 7
        ComponentKind.DOCS -> 8
        ComponentKind.OTHER -> 9
    }

    private fun ComponentKind.label(): String = when (this) {
        ComponentKind.MODEL -> "model"
        ComponentKind.PORT -> "contract"
        ComponentKind.SERVICE -> "service"
        ComponentKind.ADAPTER -> "adapter"
        ComponentKind.REGISTRY -> "registry"
        ComponentKind.CLI -> "CLI"
        ComponentKind.JOB -> "job"
        ComponentKind.TEST -> "tests"
        ComponentKind.DOCS -> "docs"
        ComponentKind.OTHER -> "component"
    }

    private fun stableNodeId(componentId: String): String =
        UUID.nameUUIDFromBytes("blueprint-component:$componentId".toByteArray(Charsets.UTF_8)).toString()

    private val DEPENDENCY_EDGES = setOf(
        EdgeKind.CALLS,
        EdgeKind.EXTENDS,
        EdgeKind.REGISTERS_WITH,
        EdgeKind.LISTENS,
    )
}
