package com.blueprint.ui

import com.blueprint.ir.ArchitectureIR
import com.blueprint.ir.Component
import com.blueprint.ir.ComponentKind
import com.blueprint.ir.Edge
import com.blueprint.ir.EdgeKind
import com.blueprint.ir.EdgeTargetKind
import com.blueprint.ir.Field
import com.blueprint.ir.Module
import com.blueprint.ir.Operation

/**
 * Pure projection from the recovered architecture IR into the canvas model.
 * The canvas should read as current code first; generated workflow state is
 * optional metadata layered onto those code entities.
 */
object CodeMapProjection {
    enum class GroupMode {
        DEPENDENCY,
        PACKAGE,
        LAYER,
        ;

        override fun toString(): String =
            when (this) {
                DEPENDENCY -> "Dependency"
                PACKAGE -> "Package"
                LAYER -> "Layer"
            }
    }

    data class Options(
        val groupMode: GroupMode = GroupMode.DEPENDENCY,
        val hideTests: Boolean = false,
        val hideGenerated: Boolean = false,
        val hideExternalEdges: Boolean = false,
        val hideLowConfidenceEdges: Boolean = false,
        val lowConfidenceThreshold: Double = 0.7,
    )

    data class WorkflowBadge(
        val status: String,
        val ready: Boolean,
        val blocked: Boolean,
        val selected: Boolean,
    )

    fun fromIr(
        ir: ArchitectureIR,
        selectedId: String?,
        workflowByComponentId: Map<String, WorkflowBadge> = emptyMap(),
        options: Options = Options(),
    ): List<MiniGraphPanel.NodeView> {
        if (ir.components.isEmpty()) return emptyList()

        val modulesByComponentId = ir.modules
            .flatMap { module -> module.componentIds.map { componentId -> componentId to module } }
            .toMap()
        val visibleComponents = ir.components
            .filterNot { options.hideTests && it.kind == ComponentKind.TEST }
            .filterNot { options.hideGenerated && it.isGeneratedBlueprintCode() }
        val componentsById = visibleComponents.associateBy { it.id }
        val componentEdges = ir.edges
            .filter { it.toKind == EdgeTargetKind.COMPONENT }
            .filter { it.from in componentsById && it.to in componentsById }
            .filterNot { options.hideExternalEdges && it.isExternalOrLibraryEdge() }
            .filterNot { options.hideLowConfidenceEdges && it.confidence < options.lowConfidenceThreshold }
        val dependenciesById = componentEdges
            .groupBy({ it.to }, { it.from })
            .mapValues { (_, deps) -> deps.distinct() }
        val outgoingEdgesById = componentEdges.groupBy { it.from }
        val incomingEdgesById = componentEdges.groupBy { it.to }

        val waveMemo = mutableMapOf<String, Int>()
        fun waveOf(id: String, visiting: Set<String> = emptySet()): Int {
            waveMemo[id]?.let { return it }
            if (id in visiting) return 1
            val deps = dependenciesById[id].orEmpty().filter { it in componentsById }
            val wave = if (deps.isEmpty()) {
                1
            } else {
                deps.maxOf { waveOf(it, visiting + id) + 1 }.coerceAtMost(8)
            }
            waveMemo[id] = wave
            return wave
        }
        val groupInfoById = groupInfoByComponent(
            components = visibleComponents,
            modulesByComponentId = modulesByComponentId,
            waveOf = { id -> waveOf(id) },
            options = options,
        )

        return visibleComponents
            .sortedWith(compareBy<Component>(
                { groupInfoById.getValue(it.id).order },
                { it.ownership.files.firstOrNull() ?: "" },
                { it.name },
            ))
            .map { component ->
                val workflow = workflowByComponentId[component.id]
                val sourceTarget = component.sourceTarget()
                val groupInfo = groupInfoById.getValue(component.id)
                MiniGraphPanel.NodeView(
                    id = component.id,
                    title = component.name,
                    status = workflow?.status ?: "CODE",
                    wave = waveOf(component.id),
                    dependencies = dependenciesById[component.id].orEmpty(),
                    selected = workflow?.selected == true || selectedId == component.id || selectedId == component.name,
                    ready = workflow?.ready ?: true,
                    blocked = workflow?.blocked ?: false,
                    detail = detailFor(component),
                    origin = MiniGraphPanel.NodeOrigin.CODE,
                    kind = component.kind.codeMapLabel(),
                    source = sourceTarget.label,
                    sourcePath = sourceTarget.path,
                    sourceLine = sourceTarget.line,
                    preview = previewFor(component),
                    fields = component.fields.take(3).map { it.cardLabel() },
                    fieldOverflowCount = (component.fields.size - 3).coerceAtLeast(0),
                    methods = component.operations.take(2).map { it.cardLabel() },
                    methodOverflowCount = (component.operations.size - 2).coerceAtLeast(0),
                    relationshipHint = relationshipHintFor(
                        incoming = incomingEdgesById[component.id].orEmpty(),
                        outgoing = outgoingEdgesById[component.id].orEmpty(),
                    ),
                    groupTitle = groupInfo.title,
                    groupOrder = groupInfo.order,
                )
            }
    }

    private fun detailFor(component: Component): String = buildString {
        append("Current code entity")
        append("\nType: ${component.kind.codeMapLabel()}")
        component.primarySource().takeIf { it.isNotBlank() }?.let { append("\nSource: $it") }
        if (component.fields.isNotEmpty()) {
            append("\nFields:")
            component.fields.take(8).forEach { append("\n- ${it.name}: ${it.type}") }
            if (component.fields.size > 8) append("\n- +${component.fields.size - 8} more field(s)")
        }
        if (component.operations.isNotEmpty()) {
            append("\nOperations:")
            component.operations.take(6).forEach { append("\n- ${it.name}()") }
            if (component.operations.size > 6) append("\n- +${component.operations.size - 6} more operation(s)")
        }
    }

    private fun previewFor(component: Component): String {
        if (component.fields.isNotEmpty()) {
            return "fields: " + component.fields.take(3).joinToString(", ") { it.name } +
                (if (component.fields.size > 3) ", +" + (component.fields.size - 3) else "")
        }
        if (component.operations.isNotEmpty()) {
            return "ops: " + component.operations.take(3).joinToString(", ") { "${it.name}()" } +
                (if (component.operations.size > 3) ", +" + (component.operations.size - 3) else "")
        }
        return component.description.substringBefore('.').take(48)
    }

    private fun Component.primarySource(): String =
        sourceTarget().label

    private fun Component.sourceTarget(): SourceTarget {
        val ref = sourceRef
        val fallbackPath = ownership.files.firstOrNull().orEmpty()
        val path = ref?.path?.takeIf { it.isNotBlank() } ?: fallbackPath
        val line = ref?.line
        val label = when {
            path.isBlank() -> ""
            line != null -> "$path:$line"
            else -> path
        }
        return SourceTarget(path = path, line = line, label = label)
    }

    private fun Field.cardLabel(): String =
        buildString {
            append(name)
            val typeId = type.id.takeIf { it.isNotBlank() && it != "Any" } ?: return@buildString
            append(": ")
            append(typeId)
        }

    private fun Operation.cardLabel(): String =
        buildString {
            append(name)
            append("()")
        }

    private fun relationshipHintFor(
        incoming: List<Edge>,
        outgoing: List<Edge>,
    ): String {
        if (incoming.isEmpty() && outgoing.isEmpty()) return ""
        val hints = (outgoing + incoming)
            .map { edge -> edge.label.ifBlank { edge.kind.codeMapLabel() } }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val counts = "edges: ${incoming.size} in / ${outgoing.size} out"
        val labels = if (hints.isEmpty()) "" else hints.take(2).joinToString(", ") + if (hints.size > 2) ", +${hints.size - 2}" else ""
        return listOf(counts, labels).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun groupInfoByComponent(
        components: List<Component>,
        modulesByComponentId: Map<String, Module>,
        waveOf: (String) -> Int,
        options: Options,
    ): Map<String, GroupInfo> {
        val labelsById = components.associate { component ->
            component.id to when (options.groupMode) {
                GroupMode.DEPENDENCY -> "Wave ${waveOf(component.id)}"
                GroupMode.PACKAGE -> packageLabel(component, modulesByComponentId[component.id])
                GroupMode.LAYER -> layerLabel(component)
            }
        }
        val orderByLabel = when (options.groupMode) {
            GroupMode.DEPENDENCY -> labelsById.values.distinct().associateWith { label ->
                label.substringAfter("Wave ", "1").toIntOrNull() ?: 1
            }
            GroupMode.LAYER -> layerOrderByLabel
            GroupMode.PACKAGE -> labelsById.values.distinct().sorted().mapIndexed { index, label -> label to index + 1 }.toMap()
        }
        return labelsById.mapValues { (_, label) ->
            GroupInfo(label, orderByLabel[label] ?: 99)
        }
    }

    private fun packageLabel(component: Component, module: Module?): String {
        val raw = module?.name?.takeIf { it.isNotBlank() }
            ?: module?.path?.trimEnd('/')
            ?: component.sourceTarget().path.substringBeforeLast("/", "")
        val label = raw.ifBlank { "(root)" }
        return "Package: $label"
    }

    private fun layerLabel(component: Component): String =
        when {
            component.tags.contains("route") -> "Layer: API"
            component.kind == ComponentKind.CLI -> "Layer: API"
            component.kind == ComponentKind.SERVICE -> "Layer: Service"
            component.kind == ComponentKind.MODEL -> "Layer: Model"
            component.kind == ComponentKind.ADAPTER -> "Layer: Adapter"
            component.kind == ComponentKind.PORT -> "Layer: Port"
            component.kind == ComponentKind.TEST -> "Layer: Test"
            else -> "Layer: Other"
        }

    private fun Component.isGeneratedBlueprintCode(): Boolean =
        ownership.files.any { it.startsWith("blueprint_demo/") } ||
            sourceRef?.path?.startsWith("blueprint_demo/") == true ||
            tags.contains("scaffold") ||
            id.startsWith("imported.")

    private fun Edge.isExternalOrLibraryEdge(): Boolean =
        label.equals("import", ignoreCase = true) ||
            evidence.startsWith("imported symbol:", ignoreCase = true) ||
            confidence <= 0.65

    private fun ComponentKind.codeMapLabel(): String =
        name.lowercase().replace('_', ' ')

    private fun EdgeKind.codeMapLabel(): String =
        name.lowercase().replace('_', ' ')

    private data class SourceTarget(
        val path: String,
        val line: Int?,
        val label: String,
    )

    private data class GroupInfo(
        val title: String,
        val order: Int,
    )

    private val layerOrderByLabel = mapOf(
        "Layer: API" to 1,
        "Layer: Service" to 2,
        "Layer: Adapter" to 3,
        "Layer: Port" to 4,
        "Layer: Model" to 5,
        "Layer: Test" to 6,
        "Layer: Other" to 7,
    )
}
