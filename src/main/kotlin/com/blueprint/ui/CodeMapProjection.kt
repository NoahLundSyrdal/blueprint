package com.blueprint.ui

import com.blueprint.ir.ArchitectureIR
import com.blueprint.ir.Component
import com.blueprint.ir.ComponentKind
import com.blueprint.ir.EdgeTargetKind

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
        val dependenciesById = ir.edges
            .filter { it.toKind == EdgeTargetKind.COMPONENT }
            .filter { it.from in componentsById && it.to in componentsById }
            .groupBy({ it.to }, { it.from })
            .mapValues { (_, deps) -> deps.distinct() }

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
                    source = component.primarySource(),
                    preview = previewFor(component),
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
        ownership.files.firstOrNull() ?: sourceRef?.path.orEmpty()

    private fun ComponentKind.codeMapLabel(): String =
        name.lowercase().replace('_', ' ')
}
