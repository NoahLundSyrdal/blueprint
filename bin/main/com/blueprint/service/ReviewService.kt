package com.blueprint.service

import com.blueprint.model.BlueprintNode
import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.ReviewArtifact
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Optional second opinion via diff_review_safety_prompt. Not required for MVP
 * apply flow, but wired so the UI can call it.
 */
@Service(Service.Level.PROJECT)
class ReviewService(private val project: Project) {

    private val log = Logger.getInstance(ReviewService::class.java)

    fun review(node: BlueprintNode, exec: ExecutionArtifact): ReviewArtifact {
        val tpl = service<PromptTemplateService>().getPrompt("diff_review_safety_prompt")
        val relevantFiles = project.service<ProjectContextCollector>().collectRelevantFiles(node)

        val ctx = mapOf(
            "NODE_DEFINITION" to JsonExtractor.toJson(node),
            "ACCEPTANCE_CRITERIA" to JsonExtractor.toJson(node.acceptanceCriteria),
            "PROJECT_INVARIANTS" to JsonExtractor.toJson(node.invariants),
            "FILE_SCOPE" to JsonExtractor.toJson(node.fileScope),
            "DEPENDENCY_OUTPUTS" to "[]",
            "PATCHES" to JsonExtractor.toJson(exec.patches),
            "RELEVANT_FILES" to relevantFiles.ifBlank { "(none supplied)" },
            "TEST_RESULTS" to "",
        )
        val rendered = service<PromptTemplateService>().renderPrompt(tpl, ctx)
        val res = service<CodexClient>().sendPromptResult(rendered)
        if (!res.ok) {
            log.warn("Review call failed: ${res.error}")
            return ReviewArtifact(
                reviewStatus = "REQUEST_CHANGES",
                summary = "LLM call failed: ${res.error}",
            )
        }
        return JsonExtractor.parseReview(res.text)
    }

    fun reviewAsync(
        node: BlueprintNode,
        exec: ExecutionArtifact,
        onDone: (ReviewArtifact) -> Unit,
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val r = review(node, exec)
            ApplicationManager.getApplication().invokeLater { onDone(r) }
        }
    }
}
