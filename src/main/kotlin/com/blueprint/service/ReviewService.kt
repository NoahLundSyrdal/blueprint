package com.blueprint.service

import com.blueprint.model.BlueprintNode
import com.blueprint.model.AcceptanceReviewItem
import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.ReviewArtifact
import com.blueprint.model.ReviewIssue
import com.blueprint.model.ScopeCompliance
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
        if (node.metadata["componentKind"] == "model_group") {
            return reviewAggregateModels(node, exec)
        }

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

    private fun reviewAggregateModels(node: BlueprintNode, exec: ExecutionArtifact): ReviewArtifact {
        val outOfScope = exec.patches.filterNot { node.fileScope.allows(it.path) }
        val combined = exec.patches.joinToString("\n\n") { it.content }
        val missing = node.outputs.flatMap { output ->
            val classMissing = if (!Regex("""\bclass\s+${Regex.escape(output.name)}\b""").containsMatchIn(combined)) {
                listOf("${output.name} class")
            } else {
                emptyList()
            }
            val fieldMissing = output.schema
                .lines()
                .map { it.substringBefore(":").trim() }
                .filter { it.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*""")) }
                .filterNot { field ->
                    Regex("""\b${Regex.escape(field)}\b""").containsMatchIn(combined)
                }
                .map { "${output.name}.$it" }
            classMissing + fieldMissing
        }

        val issues = buildList {
            outOfScope.forEach {
                add(ReviewIssue(
                    severity = "HIGH",
                    category = "scope",
                    title = "Out-of-scope patch",
                    details = "${it.path} is outside this node's file scope.",
                    suggestedFix = "Only change files inside FILE_SCOPE.",
                ))
            }
            if (exec.patches.isEmpty()) {
                add(ReviewIssue(
                    severity = "HIGH",
                    category = "correctness",
                    title = "No patch generated",
                    details = "Blueprint did not produce a models patch.",
                    suggestedFix = "Regenerate the code diff.",
                ))
            }
            if (missing.isNotEmpty()) {
                add(ReviewIssue(
                    severity = "HIGH",
                    category = "contract",
                    title = "Missing UML declarations",
                    details = "Generated code is missing: ${missing.joinToString(", ")}.",
                    suggestedFix = "Regenerate with all UML classes and fields represented.",
                ))
            }
        }

        val approved = issues.isEmpty()
        val changes = exec.patches
            .flatMap { patch ->
                val existing = project.service<ApplyChangesService>().readCurrentContent(patch.path)
                PythonModelModuleRenderer.describeChanges(existing, patch.content)
            }
            .distinct()
            .ifEmpty { listOf("no model changes") }
        return ReviewArtifact(
            reviewStatus = if (approved) "APPROVE" else "REQUEST_CHANGES",
            summary = if (approved) {
                "Approved by deterministic UML model review. Changes: ${changes.joinToString("; ")}."
            } else {
                "Model patch needs revision before apply."
            },
            scopeCompliance = ScopeCompliance(
                result = if (outOfScope.isEmpty()) "PASS" else "FAIL",
                notes = if (outOfScope.isEmpty()) listOf("All model patches are inside file scope.") else outOfScope.map { "${it.path} is out of scope." },
            ),
            acceptanceReview = node.acceptanceCriteria.map {
                AcceptanceReviewItem(
                    criterion = it.id.ifBlank { it.description },
                    result = if (approved) "PASS" else "FAIL",
                    evidence = if (approved) listOf("All declared UML model classes and fields were found in the generated patch.") else missing,
                    issues = issues.map { issue -> issue.title },
                )
            },
            issues = issues,
            positiveSignals = if (approved) {
                listOf("Deterministic schema review avoided LLM false negatives.", "Changed symbols: ${changes.joinToString("; ")}.")
            } else {
                emptyList()
            },
            recommendedNextAction = if (approved) "apply" else "revise",
            followUpChecks = listOf("Preview the diff before applying."),
        )
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
