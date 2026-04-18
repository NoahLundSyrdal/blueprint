package com.blueprint.service

import com.blueprint.ir.EdgeTargetKind
import com.blueprint.ir.IRStore
import com.blueprint.ir.MermaidProjector
import com.blueprint.ir.PythonIRExtractor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Public facade that now delegates through the architecture IR.
 *
 * Loop:
 *   1. PythonIRExtractor  — Python sources → ArchitectureIR
 *   2. IRStore            — persist .idea/blueprint/ir.json
 *   3. MermaidProjector   — IR → Mermaid class diagram
 *
 * The returned GeneratedUml shape is unchanged so existing UI wiring in
 * BlueprintPanel continues to work.
 */
@Service(Service.Level.PROJECT)
class PythonUmlGenerator(private val project: Project) {

    data class GeneratedUml(
        val text: String,
        val classCount: Int,
        val relationshipCount: Int,
        val filesScanned: Int,
        val warnings: List<String>,
    )

    fun generate(maxDepth: Int = 8): GeneratedUml {
        val extracted = project.service<PythonIRExtractor>().extract(maxDepth)
        project.service<IRStore>().save(extracted.ir)

        val mermaid = project.service<MermaidProjector>().render(extracted.ir, extracted.filesScanned)
        val relationshipCount = extracted.ir.edges.count { it.toKind == EdgeTargetKind.COMPONENT }

        val warnings = buildList {
            addAll(extracted.warnings)
            extracted.ir.components
                .filter { it.fields.isEmpty() && it.operations.isEmpty() && !it.description.startsWith("Protocol") }
                .take(8)
                .forEach { add("${it.name} has no parsed fields or public methods.") }
            val coverage = extracted.ir.coverage
            if (coverage != null && coverage.opaqueZones.isNotEmpty()) {
                add("${coverage.opaqueZones.size} opaque annotation(s) preserved in IR.")
            }
        }

        return GeneratedUml(
            text = mermaid,
            classCount = extracted.ir.components.size,
            relationshipCount = relationshipCount,
            filesScanned = extracted.filesScanned,
            warnings = warnings,
        )
    }
}
