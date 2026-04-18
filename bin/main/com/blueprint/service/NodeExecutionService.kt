package com.blueprint.service

import com.blueprint.model.BlueprintNode
import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.Patch
import com.blueprint.model.PlanArtifact
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
