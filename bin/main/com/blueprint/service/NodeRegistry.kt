package com.blueprint.service

import com.blueprint.model.BlueprintNode
import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.PlanArtifact
import com.blueprint.model.ReviewArtifact
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.EventListener

/**
 * Project-local store of nodes + per-node artifacts.
 *
 * V1.5 persistence intentionally stays boring: a single JSON file at
 * .idea/blueprint/state.json. It survives project reopen without adding a DB
 * or threading plugin state through every UI class.
 */
@Service(Service.Level.PROJECT)
class NodeRegistry(private val project: Project) : Disposable {

    interface Listener : EventListener {
        fun changed()
    }

    data class PersistedState(
        val version: Int = 1,
        val nodes: List<BlueprintNode> = emptyList(),
        val plans: Map<String, PlanArtifact> = emptyMap(),
        val executions: Map<String, ExecutionArtifact> = emptyMap(),
        val reviews: Map<String, ReviewArtifact> = emptyMap(),
        val selectedNodeId: String? = null,
    )

    private val log = Logger.getInstance(NodeRegistry::class.java)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dispatcher = EventDispatcher.create(Listener::class.java)
    private val nodes = mutableListOf<BlueprintNode>()
    private val plans = mutableMapOf<String, PlanArtifact>()
    private val executions = mutableMapOf<String, ExecutionArtifact>()
    private val reviews = mutableMapOf<String, ReviewArtifact>()
    private var selectedNodeId: String? = null

    init {
        load()
    }

    fun addListener(l: Listener) = dispatcher.addListener(l)

    fun all(): List<BlueprintNode> = nodes.toList()

    fun add(node: BlueprintNode) {
        nodes += node
        selectedNodeId = node.id
        save()
        fire()
    }

    fun remove(id: String) {
        nodes.removeAll { it.id == id }
        plans.remove(id); executions.remove(id); reviews.remove(id)
        if (selectedNodeId == id) selectedNodeId = nodes.firstOrNull()?.id
        save()
        fire()
    }

    fun update(node: BlueprintNode) {
        val idx = nodes.indexOfFirst { it.id == node.id }
        if (idx >= 0) {
            nodes[idx] = node
            save()
            fire()
        }
    }

    fun find(id: String): BlueprintNode? = nodes.firstOrNull { it.id == id }

    fun setPlan(id: String, plan: PlanArtifact) { plans[id] = plan; save(); fire() }
    fun getPlan(id: String): PlanArtifact? = plans[id]

    fun setExecution(id: String, ex: ExecutionArtifact) { executions[id] = ex; save(); fire() }
    fun getExecution(id: String): ExecutionArtifact? = executions[id]

    fun setReview(id: String, r: ReviewArtifact) { reviews[id] = r; save(); fire() }
    fun getReview(id: String): ReviewArtifact? = reviews[id]

    fun selectedNodeId(): String? = selectedNodeId

    fun setSelectedNode(id: String?) {
        if (selectedNodeId == id) return
        selectedNodeId = id
        save()
        fire()
    }

    fun persistedFile(): Path? = stateFileOrNull()

    private fun fire() = dispatcher.multicaster.changed()

    private fun snapshot(): PersistedState =
        PersistedState(
            nodes = nodes.toList(),
            plans = plans.toMap(),
            executions = executions.toMap(),
            reviews = reviews.toMap(),
            selectedNodeId = selectedNodeId,
        )

    private fun load() {
        val file = stateFileOrNull() ?: return
        if (!Files.isRegularFile(file)) return
        try {
            val json = Files.readString(file, StandardCharsets.UTF_8)
            val type = object : TypeToken<PersistedState>() {}.type
            val state = gson.fromJson(json, type) as? PersistedState ?: return
            nodes.clear()
            nodes += state.nodes
            plans.clear()
            plans += state.plans
            executions.clear()
            executions += state.executions
            reviews.clear()
            reviews += state.reviews
            selectedNodeId = state.selectedNodeId?.takeIf { id -> nodes.any { it.id == id } }
                ?: nodes.firstOrNull()?.id
            log.info("Loaded Blueprint state from $file (${nodes.size} nodes)")
        } catch (t: Throwable) {
            log.warn("Failed to load Blueprint state from $file", t)
        }
    }

    private fun save() {
        val file = stateFileOrNull() ?: return
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(snapshot()), StandardCharsets.UTF_8)
        } catch (t: Throwable) {
            log.warn("Failed to save Blueprint state to $file", t)
        }
    }

    private fun stateFileOrNull(): Path? {
        val basePath = project.basePath ?: return null
        return Paths.get(basePath).resolve(".idea").resolve("blueprint").resolve("state.json")
    }

    override fun dispose() {
        save()
    }
}
