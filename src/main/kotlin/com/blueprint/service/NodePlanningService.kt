package com.blueprint.service

import com.blueprint.model.BlueprintNode
import com.blueprint.model.PlanArtifact
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class NodePlanningService(private val project: Project) {

    private val log = Logger.getInstance(NodePlanningService::class.java)

    /** Produce a PlanArtifact for the given node using plan_generation_prompt. */
    fun generatePlan(node: BlueprintNode): PlanArtifact {
        val tpl = service<PromptTemplateService>().getPrompt("plan_generation_prompt")
        val relevantFiles = project.service<ProjectContextCollector>().collectRelevantFiles(node)
        val pythonContext = project.service<PythonProjectAnalyzer>().analyze()

        val ctx = mapOf(
            "PROJECT_SUMMARY" to "${project.name} (Python project: ${pythonContext.isPythonLikely()}, package manager: ${pythonContext.packageManager})",
            "ARCHITECTURE_CONVENTIONS" to "Follow existing Python project patterns in this project.\n\n${pythonContext.promptContext()}",
            "NODE_DEFINITION" to JsonExtractor.toJson(node),
            "DEPENDENCY_OUTPUTS" to "[]",
            "FILE_SCOPE" to JsonExtractor.toJson(node.fileScope),
            "PROJECT_INVARIANTS" to JsonExtractor.toJson(node.invariants),
            "RELEVANT_FILES" to relevantFiles.ifBlank { "(none supplied)" },
            "ACCEPTANCE_CRITERIA" to JsonExtractor.toJson(node.acceptanceCriteria),
            "TEST_COMMANDS" to pythonContext.testCommands.joinToString("\n").ifBlank { "(none inferred)" },
        )
        val rendered = service<PromptTemplateService>().renderPrompt(tpl, ctx)

        val res = service<CodexClient>().sendPromptResult(rendered)
        if (!res.ok) {
            log.warn("Plan call failed: ${res.error}")
            return PlanArtifact(
                status = "BLOCKED",
                nodeIntent = "LLM call failed: ${res.error}",
                blockingIssues = listOf(res.error ?: "Unknown error"),
                rawJson = ""
            )
        }
        return JsonExtractor.parsePlan(res.text)
    }

    fun generatePlanAsync(node: BlueprintNode, onDone: (PlanArtifact) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val plan = generatePlan(node)
            ApplicationManager.getApplication().invokeLater { onDone(plan) }
        }
    }
}
