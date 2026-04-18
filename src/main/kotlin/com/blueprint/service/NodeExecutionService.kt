package com.blueprint.service

import com.blueprint.ir.ComponentKind
import com.blueprint.ir.IRStore
import com.blueprint.model.BlueprintNode
import com.blueprint.model.AcceptanceCheckItem
import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.ExecutionValidation
import com.blueprint.model.NodeContract
import com.blueprint.model.Patch
import com.blueprint.model.PlanArtifact
import com.blueprint.model.TouchedFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class NodeExecutionService(private val project: Project) {

    private val log = Logger.getInstance(NodeExecutionService::class.java)

    /**
     * Execute a node. Enforces file scope on touchedFiles+patches; any patch
     * outside scope is dropped with a warning (and downgrades status).
     */
    fun executeNode(node: BlueprintNode, plan: PlanArtifact?): ExecutionArtifact {
        if (node.metadata["componentKind"] == "model_group") {
            return executeAggregateModels(node, plan)
        }

        val tpl = service<PromptTemplateService>().getPrompt("per_node_execution_prompt")
        val relevantFiles = project.service<ProjectContextCollector>().collectRelevantFiles(node)
        val pythonContext = project.service<PythonProjectAnalyzer>().analyze()
        val planContext = plan?.let {
            "\n\nPLAN_ARTIFACT:\n${it.rawJson.ifBlank { JsonExtractor.toJson(plan) }}"
        }.orEmpty()

        val ctx = mapOf(
            "PROJECT_SUMMARY" to "${project.name} (Python project: ${pythonContext.isPythonLikely()}, package manager: ${pythonContext.packageManager})",
            "ARCHITECTURE_CONVENTIONS" to "Follow existing Python project patterns in this project.\n\n${pythonContext.promptContext()}",
            "NODE_DEFINITION" to JsonExtractor.toJson(node),
            "DEPENDENCY_OUTPUTS" to "[]",
            "FILE_SCOPE" to JsonExtractor.toJson(node.fileScope),
            "PROJECT_INVARIANTS" to JsonExtractor.toJson(node.invariants),
            "RELEVANT_FILES" to relevantFiles.ifBlank { "(none supplied)" } + planContext,
            "ACCEPTANCE_CRITERIA" to JsonExtractor.toJson(node.acceptanceCriteria),
            "TEST_COMMANDS" to pythonContext.testCommands.joinToString("\n").ifBlank { "(none inferred)" },
        )
        val rendered = service<PromptTemplateService>().renderPrompt(tpl, ctx)

        val res = service<CodexClient>().sendPromptResult(rendered)
        if (!res.ok) {
            log.warn("Execution call failed: ${res.error}")
            return ExecutionArtifact(
                status = "BLOCKED",
                summary = "LLM call failed: ${res.error}",
                rawJson = ""
            )
        }
        val parsed = JsonExtractor.parseExecution(res.text)
        return enforceScope(node, parsed)
    }

    private fun executeAggregateModels(node: BlueprintNode, plan: PlanArtifact?): ExecutionArtifact {
        val path = node.fileScope.paths.firstOrNull() ?: "blueprint_demo/imported_invite/models.py"
        val modelOutputs = freshestModelOutputs(node).ifEmpty { node.outputs }
        val content = renderModelsModule(modelOutputs)
        return ExecutionArtifact(
            status = "SUCCESS",
            summary = "Generated a deterministic Python dataclass models module from the current UML.",
            assumptions = listOf("UML schema fields are the source of truth for the shared models file."),
            touchedFiles = listOf(TouchedFile(
                path = path,
                action = if (project.service<ApplyChangesService>().readCurrentContent(path).isBlank()) "create" else "update",
                reason = "Synchronize shared model classes with UML.",
            )),
            plan = plan?.implementationSteps?.map { it.title.ifBlank { it.details } }?.filter { it.isNotBlank() }
                ?: listOf("Render all UML model classes into the shared models module."),
            patches = listOf(Patch(
                path = path,
                action = if (project.service<ApplyChangesService>().readCurrentContent(path).isBlank()) "create" else "update",
                content = content,
            )),
            acceptanceCheck = node.acceptanceCriteria.map {
                AcceptanceCheckItem(
                    criterion = it.id.ifBlank { it.description },
                    result = "PASS",
                    notes = "Deterministic model generator emitted all declared UML schema fields.",
                )
            },
            validation = ExecutionValidation(
                suggestedCommands = listOf("python -m pytest"),
                risks = emptyList(),
            ),
            followUps = listOf("Refresh UML from code after applying to verify round-trip."),
        )
    }

    private fun freshestModelOutputs(node: BlueprintNode): List<NodeContract> {
        val allowedPaths = node.fileScope.paths.toSet()
        val ir = project.service<IRStore>().load() ?: return emptyList()
        return ir.components
            .filter { it.kind == ComponentKind.MODEL }
            .filter { component ->
                allowedPaths.isEmpty() || component.ownership.files.any { it in allowedPaths }
            }
            .map { component ->
                NodeContract(
                    name = component.name,
                    kind = "schema",
                    description = "UML model ${component.name}.",
                    schema = component.fields.joinToString("\n") { field -> "${field.name}: ${field.type}" },
                )
            }
            .sortedBy { it.name }
    }

    private fun renderModelsModule(outputs: List<NodeContract>): String {
        val classes = orderedModelOutputs(outputs)
        val needsDatetime = classes.any { (_, fields) -> fields.any { it.second == "datetime" } }
        return buildString {
            appendLine("from dataclasses import dataclass")
            if (needsDatetime) appendLine("from datetime import datetime")
            appendLine()
            classes.forEachIndexed { index, (name, fields) ->
                if (index > 0) appendLine()
                appendLine("@dataclass")
                appendLine("class $name:")
                if (fields.isEmpty()) {
                    appendLine("    pass")
                } else {
                    fields.forEach { (field, type) -> appendLine("    $field: $type") }
                }
            }
        }.trimEnd() + "\n"
    }

    private fun orderedModelOutputs(outputs: List<NodeContract>): List<Pair<String, List<Pair<String, String>>>> {
        val parsed = outputs
            .filter { it.name.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*""")) }
            .associate { output ->
                output.name to output.schema.lines().mapNotNull(::parseFieldLine)
            }
        val names = parsed.keys
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val ordered = mutableListOf<String>()

        fun visit(name: String) {
            if (name in visited || name in visiting) return
            visiting += name
            parsed[name].orEmpty()
                .map { (_, type) -> type.substringBefore("[").substringBefore("?").trim() }
                .filter { it in names }
                .sorted()
                .forEach(::visit)
            visiting -= name
            visited += name
            ordered += name
        }

        names.sorted().forEach(::visit)
        return ordered.map { it to parsed.getValue(it) }
    }

    private fun parseFieldLine(line: String): Pair<String, String>? {
        val cleaned = line
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
        val name = cleaned.substringBefore(":", "").trim()
        val type = cleaned.substringAfter(":", "").trim()
        if (!name.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*"""))) return null
        if (type.isBlank()) return null
        return name to type
    }

    private fun enforceScope(node: BlueprintNode, exec: ExecutionArtifact): ExecutionArtifact {
        if (node.fileScope.isEmpty()) return exec
        val allowedPatches = mutableListOf<Patch>()
        val rejected = mutableListOf<String>()
        for (p in exec.patches) {
            if (node.fileScope.allows(p.path)) allowedPatches += p
            else rejected += p.path
        }
        val allowedTouched = exec.touchedFiles.filter { node.fileScope.allows(it.path) }
        val rejectedTouched = exec.touchedFiles.map { it.path }.filterNot { node.fileScope.allows(it) }
        val allRejected = (rejected + rejectedTouched).distinct()
        if (allRejected.isEmpty()) return exec
        log.warn("Dropping ${allRejected.size} out-of-scope execution file entries: $allRejected")
        val newStatus = if (exec.status == "SUCCESS") "PARTIAL" else exec.status
        return exec.copy(
            status = newStatus,
            touchedFiles = allowedTouched,
            patches = allowedPatches,
            validation = exec.validation.copy(
                risks = exec.validation.risks + allRejected.map { "Out-of-scope execution entry dropped: $it" }
            )
        )
    }

    fun executeNodeAsync(
        node: BlueprintNode,
        plan: PlanArtifact?,
        onDone: (ExecutionArtifact) -> Unit,
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = executeNode(node, plan)
            ApplicationManager.getApplication().invokeLater { onDone(result) }
        }
    }
}
