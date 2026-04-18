package com.blueprint.service

import com.blueprint.model.BlueprintNode
import com.blueprint.model.ExecutionStatus
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Lightweight dependency analysis for MVP V2.
 *
 * This is intentionally not a scheduler/canvas. It validates the list of node
 * dependencies, computes topological waves, and answers "can this single node
 * safely run now?" without introducing parallel execution.
 */
@Service(Service.Level.PROJECT)
class DependencyGraphService(private val project: Project) {

    data class NodeReadiness(
        val nodeId: String,
        val ready: Boolean,
        val reasons: List<String> = emptyList(),
        val wave: Int? = null,
    )

    data class Wave(
        val index: Int,
        val nodeIds: List<String>,
    )

    data class GraphReport(
        val validDag: Boolean,
        val waves: List<Wave>,
        val readiness: Map<String, NodeReadiness>,
        val missingDependencies: Map<String, List<String>>,
        val cyclicNodeIds: Set<String>,
    )

    fun analyze(): GraphReport {
        val registry = project.service<NodeRegistry>()
        val nodes = registry.all()
        val nodeById = nodes.associateBy { it.id }
        val missing = nodes.associate { node ->
            node.id to node.dependencies.filterNot { nodeById.containsKey(it) }
        }.filterValues { it.isNotEmpty() }
        val cyclic = findCyclicNodes(nodes)
        val waves = if (missing.isEmpty() && cyclic.isEmpty()) computeWaves(nodes) else emptyList()
        val waveByNode = waves.flatMap { wave -> wave.nodeIds.map { it to wave.index } }.toMap()
        val readiness = nodes.associate { node ->
            node.id to readinessFor(node, nodeById, missing[node.id].orEmpty(), cyclic, waveByNode[node.id])
        }
        return GraphReport(
            validDag = missing.isEmpty() && cyclic.isEmpty(),
            waves = waves,
            readiness = readiness,
            missingDependencies = missing,
            cyclicNodeIds = cyclic,
        )
    }

    fun readinessFor(node: BlueprintNode): NodeReadiness =
        analyze().readiness[node.id] ?: NodeReadiness(node.id, ready = false, reasons = listOf("Node is not registered"))

    fun readyNodes(): List<BlueprintNode> {
        val registry = project.service<NodeRegistry>()
        val report = analyze()
        return registry.all().filter {
            report.readiness[it.id]?.ready == true &&
                it.executionStatus !in setOf(ExecutionStatus.EXECUTING, ExecutionStatus.REVIEW, ExecutionStatus.APPLIED)
        }
    }

    fun wavePreviewText(): String {
        val registry = project.service<NodeRegistry>()
        val report = analyze()
        if (!report.validDag) {
            val problems = buildList {
                report.missingDependencies.forEach { (nodeId, deps) ->
                    add("${label(nodeId, registry)} is missing dependencies: ${deps.joinToString()}")
                }
                if (report.cyclicNodeIds.isNotEmpty()) {
                    add("Cycle detected among: ${report.cyclicNodeIds.joinToString { label(it, registry) }}")
                }
            }
            return "Graph is blocked:\n" + problems.joinToString("\n")
        }
        if (report.waves.isEmpty()) return "No nodes yet."
        return report.waves.joinToString("\n") { wave ->
            "Wave ${wave.index}: " + wave.nodeIds.joinToString(", ") { label(it, registry) }
        }
    }

    private fun readinessFor(
        node: BlueprintNode,
        nodeById: Map<String, BlueprintNode>,
        missingDependencies: List<String>,
        cyclicNodeIds: Set<String>,
        wave: Int?,
    ): NodeReadiness {
        val reasons = mutableListOf<String>()
        if (missingDependencies.isNotEmpty()) {
            reasons += "Missing dependency: ${missingDependencies.joinToString()}"
        }
        if (node.id in cyclicNodeIds) {
            reasons += "Cycle: node participates in a dependency cycle"
        }
        if (node.executionStatus in setOf(ExecutionStatus.EXECUTING, ExecutionStatus.APPLIED)) {
            reasons += "Node is already ${node.executionStatus.name.lowercase()}"
        }
        for (depId in node.dependencies) {
            val dep = nodeById[depId] ?: continue
            when {
                dep.executionStatus == ExecutionStatus.APPLIED -> Unit
                dep.executionStatus in setOf(ExecutionStatus.BLOCKED, ExecutionStatus.FAILED) ->
                    reasons += "Upstream failed/blocked: '${dep.title.ifBlank { dep.id.take(8) }}' is ${dep.executionStatus.name.lowercase()}"
                else ->
                    reasons += "Upstream not applied: '${dep.title.ifBlank { dep.id.take(8) }}' is ${dep.executionStatus.name.lowercase()}"
            }
        }
        return NodeReadiness(
            nodeId = node.id,
            ready = reasons.isEmpty(),
            reasons = reasons,
            wave = wave,
        )
    }

    private fun computeWaves(nodes: List<BlueprintNode>): List<Wave> {
        val remaining = nodes.associate { it.id to it.dependencies.toMutableSet() }.toMutableMap()
        val waves = mutableListOf<Wave>()
        var waveIndex = 1
        while (remaining.isNotEmpty()) {
            val ready = remaining.filterValues { it.isEmpty() }.keys.sorted()
            if (ready.isEmpty()) return emptyList()
            waves += Wave(waveIndex++, ready)
            ready.forEach { remaining.remove(it) }
            remaining.values.forEach { deps -> deps.removeAll(ready.toSet()) }
        }
        return waves
    }

    private fun findCyclicNodes(nodes: List<BlueprintNode>): Set<String> {
        val nodeIds = nodes.map { it.id }.toSet()
        val deps = nodes.associate { it.id to it.dependencies.filter { dep -> dep in nodeIds } }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val cyclic = mutableSetOf<String>()

        fun dfs(id: String, path: List<String>) {
            if (id in visited) return
            if (id in visiting) {
                val start = path.indexOf(id).takeIf { it >= 0 } ?: 0
                cyclic += path.drop(start)
                cyclic += id
                return
            }
            visiting += id
            deps[id].orEmpty().forEach { dfs(it, path + id) }
            visiting -= id
            visited += id
        }

        nodes.forEach { dfs(it.id, emptyList()) }
        return cyclic
    }

    private fun label(nodeId: String, registry: NodeRegistry): String {
        val node = registry.find(nodeId)
        return if (node == null) nodeId else "${node.title.ifBlank { node.id.take(8) }} (${node.id.take(8)})"
    }
}
