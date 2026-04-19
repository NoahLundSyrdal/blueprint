package com.blueprint.ui

import com.blueprint.ir.ArchitectureIR
import com.blueprint.ir.Component
import com.blueprint.ir.Edge
import com.blueprint.ir.EdgeKind
import com.blueprint.service.UmlImportService

/**
 * Builds compact facts for architecture chat prompts.
 *
 * This intentionally sends selected structural facts instead of whole source
 * files: source refs, fields, methods, and relationships are enough for the
 * model to answer or refine UML in the user's current context.
 */
object ChatGroundingContext {
    data class Grounding(
        val mode: String,
        val selectedId: String?,
        val selectedLabel: String,
        val promptText: String,
    )

    fun build(
        ir: ArchitectureIR?,
        parsedUml: UmlImportService.ParsedUml?,
        selectedCanvasId: String?,
        viewingUmlDraft: Boolean,
    ): Grounding {
        val selected = selectedCanvasId?.takeIf { it.isNotBlank() }

        if (viewingUmlDraft) {
            selected?.let { id ->
                proposedUmlGrounding(parsedUml, id)?.let { return it }
            }
        }

        selected?.let { id ->
            codeGrounding(ir, id)?.let { return it }
            proposedUmlGrounding(parsedUml, id)?.let { return it }
        }

        return noSelectionGrounding(ir, parsedUml, viewingUmlDraft)
    }

    fun compactUml(uml: String, maxLines: Int = 80, maxChars: Int = 5_000): String {
        val normalized = uml.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isBlank()) return "(empty)"
        val lines = normalized.lines()
        val clippedLines = if (lines.size > maxLines) {
            lines.take(maxLines) + listOf("%% ... ${lines.size - maxLines} more line(s) omitted")
        } else {
            lines
        }
        val text = clippedLines.joinToString("\n")
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "\n%% ... omitted"
    }

    private fun codeGrounding(ir: ArchitectureIR?, selectedId: String): Grounding? {
        ir ?: return null
        val componentsById = ir.components.associateBy { it.id }
        val component = componentsById[selectedId]
            ?: ir.components.firstOrNull { it.name == selectedId }
            ?: return null
        val relatedEdges = ir.edges.filter { it.from == component.id || it.to == component.id }

        val prompt = buildString {
            appendLine("Mode: current code map")
            appendLine("Selected code entity: ${component.name}")
            appendLine("ID: ${component.id}")
            appendLine("Kind: ${component.kind.name.lowercase().replace('_', ' ')}")
            appendLine("Source: ${component.sourceLabel()}")
            if (component.fields.isNotEmpty()) {
                appendLine("Fields:")
                component.fields.take(12).forEach { field -> appendLine("- ${field.name}: ${field.type}") }
                if (component.fields.size > 12) appendLine("- +${component.fields.size - 12} more field(s)")
            }
            if (component.operations.isNotEmpty()) {
                appendLine("Methods:")
                component.operations.take(8).forEach { operation ->
                    appendLine("- ${operation.name}(${operation.params.joinToString(", ") { it.name + ": " + it.type }}) -> ${operation.returns}")
                }
                if (component.operations.size > 8) appendLine("- +${component.operations.size - 8} more method(s)")
            }
            if (relatedEdges.isNotEmpty()) {
                appendLine("Relationships:")
                relatedEdges.take(12).forEach { edge ->
                    appendLine("- ${edge.labelFor(component, componentsById)}")
                }
                if (relatedEdges.size > 12) appendLine("- +${relatedEdges.size - 12} more relationship(s)")
            }
        }.trim()

        return Grounding(
            mode = "current code map",
            selectedId = component.id,
            selectedLabel = "selected code entity ${component.name}",
            promptText = prompt,
        )
    }

    private fun proposedUmlGrounding(parsedUml: UmlImportService.ParsedUml?, selectedId: String): Grounding? {
        parsedUml ?: return null
        val entity = parsedUml.entities.firstOrNull { it.name == selectedId } ?: return null
        val relationships = parsedUml.relationships.filter { it.from == entity.name || it.to == entity.name }
        val prompt = buildString {
            appendLine("Mode: editable UML draft")
            appendLine("Selected proposed UML entity: ${entity.name}")
            if (entity.fields.isNotEmpty()) {
                appendLine("Proposed fields/methods:")
                entity.fields.take(16).forEach { appendLine("- $it") }
                if (entity.fields.size > 16) appendLine("- +${entity.fields.size - 16} more item(s)")
            }
            if (relationships.isNotEmpty()) {
                appendLine("Proposed relationships:")
                relationships.take(12).forEach { rel -> appendLine("- ${rel.from} ${rel.label} ${rel.to}") }
                if (relationships.size > 12) appendLine("- +${relationships.size - 12} more relationship(s)")
            }
        }.trim()
        return Grounding(
            mode = "editable UML draft",
            selectedId = entity.name,
            selectedLabel = "selected UML entity ${entity.name}",
            promptText = prompt,
        )
    }

    private fun noSelectionGrounding(
        ir: ArchitectureIR?,
        parsedUml: UmlImportService.ParsedUml?,
        viewingUmlDraft: Boolean,
    ): Grounding {
        val prompt = buildString {
            appendLine("Mode: ${if (viewingUmlDraft) "editable UML draft" else "current code map"}")
            appendLine("Selected entity: none")
            parsedUml?.entities?.takeIf { it.isNotEmpty() }?.let { entities ->
                appendLine("UML entities in editor: ${entities.take(12).joinToString(", ") { it.name }}")
                if (entities.size > 12) appendLine("UML entity overflow: +${entities.size - 12}")
            }
            ir?.components?.takeIf { it.isNotEmpty() }?.let { components ->
                appendLine("Code entities recovered: ${components.take(12).joinToString(", ") { it.name }}")
                if (components.size > 12) appendLine("Code entity overflow: +${components.size - 12}")
            }
        }.trim()
        return Grounding(
            mode = if (viewingUmlDraft) "editable UML draft" else "current code map",
            selectedId = null,
            selectedLabel = "no selected entity",
            promptText = prompt,
        )
    }

    private fun Component.sourceLabel(): String {
        val ref = sourceRef
        if (ref != null && ref.path.isNotBlank()) return "${ref.path}:${ref.line}"
        return ownership.files.firstOrNull()?.takeIf { it.isNotBlank() } ?: "(unknown)"
    }

    private fun Edge.labelFor(selected: Component, componentsById: Map<String, Component>): String {
        val direction = if (from == selected.id) "out" else "in"
        val otherId = if (from == selected.id) to else from
        val other = componentsById[otherId]?.name ?: otherId
        val kindLabel = kind.chatLabel()
        val edgeLabel = label.ifBlank { kindLabel }
        return "$direction $kindLabel $other ($edgeLabel)"
    }

    private fun EdgeKind.chatLabel(): String =
        name.lowercase().replace('_', ' ')
}
