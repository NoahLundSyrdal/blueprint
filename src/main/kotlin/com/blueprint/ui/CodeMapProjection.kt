package com.blueprint.ui

import com.blueprint.ir.ArchitectureIR
import com.blueprint.ir.Component
import com.blueprint.ir.ComponentKind
import com.blueprint.ir.Edge
import com.blueprint.ir.EdgeKind
import com.blueprint.ir.EdgeTargetKind
import com.blueprint.ir.Field
import com.blueprint.ir.Operation

/**
 * Pure projection from the recovered architecture IR into the canvas model.
 * The canvas should read as current code first; generated workflow state is
 * optional metadata layered onto those code entities.
 */
object CodeMapProjection {
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
    ): List<MiniGraphPanel.NodeView> {
        if (ir.components.isEmpty()) return emptyList()

        val componentsById = ir.components.associateBy { it.id }
        val componentEdges = ir.edges
            .filter { it.toKind == EdgeTargetKind.COMPONENT }
            .filter { it.from in componentsById && it.to in componentsById }
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

        return ir.components
            .sortedWith(compareBy<Component>({ waveOf(it.id) }, { it.ownership.files.firstOrNull() ?: "" }, { it.name }))
            .map { component ->
                val workflow = workflowByComponentId[component.id]
                val sourceTarget = component.sourceTarget()
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

    private fun ComponentKind.codeMapLabel(): String =
        name.lowercase().replace('_', ' ')

    private fun EdgeKind.codeMapLabel(): String =
        name.lowercase().replace('_', ' ')

    private data class SourceTarget(
        val path: String,
        val line: Int?,
        val label: String,
    )
}
