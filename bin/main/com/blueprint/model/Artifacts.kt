package com.blueprint.model

/**
 * Artifact shapes must match exactly what the packaged prompts in
 * src/main/resources/prompts tell the model to return. Do NOT drift from these shapes.
 *
 * All artifacts also retain the raw JSON string so the UI can show debug output
 * when parsing partially fails.
 */

// ---------- plan_generation_prompt ----------

data class FileTouch(
    val path: String,
    val why: String = ""
)

data class ImplementationStep(
    val id: String = "",
    val title: String = "",
    val details: String = "",
    val dependsOn: List<String> = emptyList()
)

data class CriterionMapping(
    val criterion: String,
    val howToVerify: String
)

data class ValidationPlan(
    val criteriaMapping: List<CriterionMapping> = emptyList(),
    val testsToAddOrRun: List<String> = emptyList(),
    val manualChecks: List<String> = emptyList()
)

data class PlanArtifact(
    val status: String = "READY",          // READY | BLOCKED
    val nodeIntent: String = "",
    val filesToTouch: List<FileTouch> = emptyList(),
    val implementationSteps: List<ImplementationStep> = emptyList(),
    val contractsToRespect: List<String> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val validationPlan: ValidationPlan = ValidationPlan(),
    val blockingIssues: List<String> = emptyList(),
    val rawJson: String = ""
)

// ---------- per_node_execution_prompt ----------

enum class PatchAction { create, update, delete }

data class TouchedFile(
    val path: String,
    val action: String = "update",       // create|update|delete
    val reason: String = ""
)

data class Patch(
    val path: String,
    val action: String = "update",       // create|update|delete
    val content: String = ""             // full proposed file content (MVP) or patch
)

data class AcceptanceCheckItem(
    val criterion: String,
    val result: String = "NOT_APPLICABLE", // PASS|PARTIAL|FAIL|NOT_APPLICABLE
    val notes: String = ""
)

data class ExecutionValidation(
    val testsAddedOrUpdated: List<String> = emptyList(),
    val suggestedCommands: List<String> = emptyList(),
    val risks: List<String> = emptyList()
)

data class ExecutionArtifact(
    val status: String = "BLOCKED",      // SUCCESS | BLOCKED | PARTIAL
    val summary: String = "",
    val assumptions: List<String> = emptyList(),
    val touchedFiles: List<TouchedFile> = emptyList(),
    val plan: List<String> = emptyList(),
    val patches: List<Patch> = emptyList(),
    val acceptanceCheck: List<AcceptanceCheckItem> = emptyList(),
    val validation: ExecutionValidation = ExecutionValidation(),
    val followUps: List<String> = emptyList(),
    val rawJson: String = ""
)

// ---------- diff_review_safety_prompt ----------

data class ScopeCompliance(
    val result: String = "PASS",
    val notes: List<String> = emptyList()
)

data class AcceptanceReviewItem(
    val criterion: String,
    val result: String = "UNCLEAR",       // PASS|PARTIAL|FAIL|UNCLEAR
    val evidence: List<String> = emptyList(),
    val issues: List<String> = emptyList()
)

data class ReviewIssue(
    val severity: String = "LOW",         // HIGH|MEDIUM|LOW
    val category: String = "maintainability", // scope|correctness|contract|test|safety|maintainability
    val title: String = "",
    val details: String = "",
    val suggestedFix: String = ""
)

data class ReviewArtifact(
    val reviewStatus: String = "REQUEST_CHANGES", // APPROVE | REQUEST_CHANGES | REJECT
    val summary: String = "",
    val scopeCompliance: ScopeCompliance = ScopeCompliance(),
    val acceptanceReview: List<AcceptanceReviewItem> = emptyList(),
    val issues: List<ReviewIssue> = emptyList(),
    val positiveSignals: List<String> = emptyList(),
    val recommendedNextAction: String = "revise",  // apply | revise | block
    val followUpChecks: List<String> = emptyList(),
    val rawJson: String = ""
)
