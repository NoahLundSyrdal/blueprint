package com.blueprint.service

import com.blueprint.model.AcceptanceCriterion
import com.blueprint.model.AcceptanceCriterionType
import com.blueprint.model.BlueprintNode
import com.blueprint.model.FileScope
import com.blueprint.model.NodeType
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in live smoke test for the AI path.
 *
 * Skipped by default so normal tests do not depend on network, quota, or model
 * availability. Run with scripts/test-live-ai.sh.
 */
class BlueprintLiveAiSmokeTest {

    @Test
    fun `live ai prompt chain returns parseable blueprint artifacts`() {
        assumeTrue("Set BLUEPRINT_LIVE_AI_SMOKE=1 to run live AI smoke test.",
            System.getenv("BLUEPRINT_LIVE_AI_SMOKE") == "1")

        val prompts = PromptTemplateService()
        val client = CodexClient().apply { setProviderOverride("openai") }

        assertTrue("Expected OPENAI_API_KEY from env or .env", client.hasOpenAIKey())

        val sentinel = client.sendPromptResult("Reply with exactly: blueprint-live-ai-ok")
        assertTrue(sentinel.error ?: "OpenAI sentinel call failed", sentinel.ok)
        assertTrue("Unexpected sentinel response: ${sentinel.text}", sentinel.text.contains("blueprint-live-ai-ok"))

        val uml = client.sendPromptResult(
            """
            Return only a Mermaid classDiagram.
            Include class LiveSmoke with field status: str.
            Do not wrap it in markdown.
            """.trimIndent()
        )
        assertTrue(uml.error ?: "UML chat call failed", uml.ok)
        assertTrue("Expected Mermaid classDiagram, got: ${uml.text}", uml.text.contains("classDiagram"))

        val node = BlueprintNode(
            id = "live-smoke-node",
            type = NodeType.BACKEND,
            title = "Create live AI smoke module",
            summary = "Create a tiny deterministic Python module for Blueprint live smoke testing.",
            description = "Create blueprint_demo/live_smoke.py with smoke_status() returning exactly blueprint-e2e-ok.",
            fileScope = FileScope(paths = listOf("blueprint_demo/live_smoke.py")),
            acceptanceCriteria = listOf(
                AcceptanceCriterion(
                    id = "AC1",
                    type = AcceptanceCriterionType.CODEGEN,
                    description = "blueprint_demo/live_smoke.py defines smoke_status() returning exactly blueprint-e2e-ok.",
                    verifyWith = "Review the proposed full-file patch content.",
                )
            ),
            invariants = listOf(
                "Only touch files in FILE_SCOPE.",
                "Do not use network, disk IO, subprocesses, or randomness.",
            ),
        )

        val baseContext = mapOf(
            "PROJECT_SUMMARY" to "Blueprint live smoke project (Python project: true, package manager: none)",
            "ARCHITECTURE_CONVENTIONS" to "Use simple Python 3 modules. Keep the implementation deterministic and dependency-free.",
            "NODE_DEFINITION" to JsonExtractor.toJson(node),
            "DEPENDENCY_OUTPUTS" to "[]",
            "FILE_SCOPE" to JsonExtractor.toJson(node.fileScope),
            "PROJECT_INVARIANTS" to JsonExtractor.toJson(node.invariants),
            "RELEVANT_FILES" to "(none supplied; this is a new scoped file)",
            "ACCEPTANCE_CRITERIA" to JsonExtractor.toJson(node.acceptanceCriteria),
            "TEST_COMMANDS" to "python -m pytest",
        )

        val planResult = client.sendPromptResult(
            prompts.renderPrompt(prompts.getPrompt("plan_generation_prompt"), baseContext)
        )
        assertTrue(planResult.error ?: "Plan generation call failed", planResult.ok)
        val plan = JsonExtractor.parsePlan(planResult.text)
        assertTrue("Plan schema issues: ${JsonExtractor.planIssues(plan)}\n${plan.rawJson}",
            JsonExtractor.planIssues(plan).isEmpty())
        assertTrue("Plan should be ready for this tiny scoped node", plan.status == "READY")
        assertTrue("Plan should include implementation steps", plan.implementationSteps.isNotEmpty())

        val executionResult = client.sendPromptResult(
            prompts.renderPrompt(
                prompts.getPrompt("per_node_execution_prompt"),
                baseContext + ("RELEVANT_FILES" to "(none supplied; this is a new scoped file)\n\nPLAN_ARTIFACT:\n${plan.rawJson}")
            )
        )
        assertTrue(executionResult.error ?: "Execution call failed", executionResult.ok)
        val execution = JsonExtractor.parseExecution(executionResult.text)
        assertTrue("Execution schema issues: ${JsonExtractor.executionIssues(execution)}\n${execution.rawJson}",
            JsonExtractor.executionIssues(execution).isEmpty())
        assertTrue("Execution should produce a useful proposal", execution.status in setOf("SUCCESS", "PARTIAL"))
        assertTrue("Execution should include the scoped patch",
            execution.patches.any { it.path == "blueprint_demo/live_smoke.py" })

        val reviewResult = client.sendPromptResult(
            prompts.renderPrompt(
                prompts.getPrompt("diff_review_safety_prompt"),
                mapOf(
                    "NODE_DEFINITION" to JsonExtractor.toJson(node),
                    "ACCEPTANCE_CRITERIA" to JsonExtractor.toJson(node.acceptanceCriteria),
                    "PROJECT_INVARIANTS" to JsonExtractor.toJson(node.invariants),
                    "FILE_SCOPE" to JsonExtractor.toJson(node.fileScope),
                    "DEPENDENCY_OUTPUTS" to "[]",
                    "PATCHES" to JsonExtractor.toJson(execution.patches),
                    "RELEVANT_FILES" to "(none supplied; this is a new scoped file)",
                    "TEST_RESULTS" to "Not run in live smoke test; review the full-file patch only.",
                )
            )
        )
        assertTrue(reviewResult.error ?: "Review call failed", reviewResult.ok)
        val review = JsonExtractor.parseReview(reviewResult.text)
        assertTrue("Review schema issues: ${JsonExtractor.reviewIssues(review)}\n${review.rawJson}",
            JsonExtractor.reviewIssues(review).isEmpty())
        assertTrue("Review should return a valid decision", review.reviewStatus in setOf("APPROVE", "REQUEST_CHANGES", "REJECT"))
        assertTrue("Review should include acceptance review", review.acceptanceReview.isNotEmpty())
    }
}
