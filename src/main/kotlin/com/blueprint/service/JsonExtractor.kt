package com.blueprint.service

import com.blueprint.model.AcceptanceCheckItem
import com.blueprint.model.AcceptanceReviewItem
import com.blueprint.model.CriterionMapping
import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.ExecutionValidation
import com.blueprint.model.FileTouch
import com.blueprint.model.ImplementationStep
import com.blueprint.model.Patch
import com.blueprint.model.PlanArtifact
import com.blueprint.model.ReviewArtifact
import com.blueprint.model.ReviewIssue
import com.blueprint.model.ScopeCompliance
import com.blueprint.model.TouchedFile
import com.blueprint.model.ValidationPlan
import com.intellij.openapi.diagnostic.Logger
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException

/**
 * Safely extract and parse JSON from a possibly-messy LLM response.
 *
 * Handles:
 *   - ```json fenced blocks
 *   - trailing prose after the JSON
 *   - partial/missing fields (defaults applied)
 */
object JsonExtractor {

    private val log = Logger.getInstance(JsonExtractor::class.java)
    private val gson = Gson()

    fun extractJsonBlock(raw: String): String? {
        if (raw.isBlank()) return null
        // 1. fenced ```json ... ``` or ``` ... ```
        val fence = Regex("(?s)```(?:json)?\\s*\\n?(.*?)```").find(raw)
        if (fence != null) {
            val candidate = fence.groupValues[1].trim()
            if (candidate.isNotEmpty()) return candidate
        }
        // 2. first {...} balanced span
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inStr) {
                when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
            } else {
                when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return raw.substring(start, i + 1)
                    }
                }
            }
        }
        val partial = raw.substring(start).trim()
        return closePartialJson(partial).takeIf { it.isNotBlank() }
    }

    private fun parseObject(raw: String): JsonObject? {
        val block = extractJsonBlock(raw) ?: return null
        val candidates = listOf(block, sanitizeJson(block)).distinct()
        for (candidate in candidates) {
            try {
                val el: JsonElement = com.google.gson.JsonParser.parseString(candidate)
                if (el.isJsonObject) return el.asJsonObject
            } catch (e: JsonSyntaxException) {
                log.warn("JSON parse failed: ${e.message}")
            } catch (e: Exception) {
                log.warn("JSON extract failed", e)
            }
        }
        return null
    }

    // --- helpers ---
    private fun JsonObject.str(k: String, d: String = ""): String {
        val el = get(k) ?: return d
        if (el.isJsonNull) return d
        return runCatching { el.asString }.getOrDefault(d)
    }

    private fun JsonObject.strList(k: String): List<String> {
        val el = get(k) ?: return emptyList()
        if (el.isJsonNull) return emptyList()
        if (!el.isJsonArray) return emptyList()
        return el.asJsonArray.mapNotNull { item ->
            if (item.isJsonNull) null else runCatching { item.asString }.getOrNull()
        }
    }

    private fun JsonObject.obj(k: String): JsonObject? {
        val el = get(k) ?: return null
        return if (!el.isJsonNull && el.isJsonObject) el.asJsonObject else null
    }

    private fun JsonObject.array(k: String): List<JsonObject> {
        val el = get(k) ?: return emptyList()
        if (el.isJsonNull || !el.isJsonArray) return emptyList()
        return el.asJsonArray.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
    }

    private fun oneOf(value: String, allowed: Set<String>, fallback: String): String =
        if (value in allowed) value else fallback

    private fun sanitizeJson(raw: String): String =
        raw.trim()
            .removePrefix("\uFEFF")
            .replace(Regex(""",\s*([}\]])"""), "\$1")

    private fun closePartialJson(raw: String): String {
        var inStr = false
        var esc = false
        val stack = ArrayDeque<Char>()
        for (c in raw) {
            if (inStr) {
                when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
            } else {
                when (c) {
                    '"' -> inStr = true
                    '{' -> stack.addLast('}')
                    '[' -> stack.addLast(']')
                    '}', ']' -> if (stack.isNotEmpty()) stack.removeLast()
                }
            }
        }
        return buildString {
            append(raw)
            while (stack.isNotEmpty()) append(stack.removeLast())
        }
    }

    // --- Plan ---
    fun parsePlan(raw: String): PlanArtifact {
        val json = extractJsonBlock(raw) ?: raw
        val obj = parseObject(raw) ?: return PlanArtifact(
            status = "BLOCKED",
            nodeIntent = "Could not parse plan JSON.",
            blockingIssues = listOf("Unparseable LLM output"),
            rawJson = raw
        )
        val vp = obj.obj("validationPlan")
        val validation = if (vp != null) ValidationPlan(
            criteriaMapping = vp.array("criteriaMapping").map {
                val o = it
                CriterionMapping(o.str("criterion"), o.str("howToVerify"))
            },
            testsToAddOrRun = vp.strList("testsToAddOrRun"),
            manualChecks = vp.strList("manualChecks"),
        ) else ValidationPlan()

        val status = oneOf(obj.str("status"), setOf("READY", "BLOCKED"), "BLOCKED")
        val issues = validateRequired(obj, listOf("status", "nodeIntent", "filesToTouch", "implementationSteps"))
        return PlanArtifact(
            status = status,
            nodeIntent = obj.str("nodeIntent"),
            filesToTouch = obj.array("filesToTouch").map {
                val o = it
                FileTouch(o.str("path"), o.str("why"))
            },
            implementationSteps = obj.array("implementationSteps").map {
                val o = it
                ImplementationStep(
                    id = o.str("id"),
                    title = o.str("title"),
                    details = o.str("details"),
                    dependsOn = o.strList("dependsOn"),
                )
            },
            contractsToRespect = obj.strList("contractsToRespect"),
            assumptions = obj.strList("assumptions"),
            risks = obj.strList("risks"),
            validationPlan = validation,
            blockingIssues = obj.strList("blockingIssues") +
                issues.map { "Schema issue: $it" } +
                if (status == "BLOCKED" && obj.str("status").isBlank()) listOf("Missing required status") else emptyList(),
            rawJson = json,
        )
    }

    // --- Execution ---
    fun parseExecution(raw: String): ExecutionArtifact {
        val json = extractJsonBlock(raw) ?: raw
        val obj = parseObject(raw) ?: return ExecutionArtifact(
            status = "BLOCKED",
            summary = "Could not parse execution JSON.",
            rawJson = raw
        )
        val vObj = obj.obj("validation")
        val validation = if (vObj != null) ExecutionValidation(
            testsAddedOrUpdated = vObj.strList("testsAddedOrUpdated"),
            suggestedCommands = vObj.strList("suggestedCommands"),
            risks = vObj.strList("risks"),
        ) else ExecutionValidation()

        val status = oneOf(obj.str("status"), setOf("SUCCESS", "BLOCKED", "PARTIAL"), "BLOCKED")
        val issues = validateRequired(obj, listOf("status", "summary", "touchedFiles", "patches", "acceptanceCheck", "validation"))
        return ExecutionArtifact(
            status = status,
            summary = obj.str("summary").ifBlank {
                if (issues.isEmpty()) "" else "Execution output had schema issues: ${issues.joinToString("; ")}"
            },
            assumptions = obj.strList("assumptions"),
            touchedFiles = obj.array("touchedFiles").map {
                val o = it
                TouchedFile(o.str("path"), o.str("action", "update"), o.str("reason"))
            },
            plan = obj.strList("plan"),
            patches = obj.array("patches").map {
                val o = it
                Patch(o.str("path"), o.str("action", "update"), o.str("content"))
            },
            acceptanceCheck = obj.array("acceptanceCheck").map {
                val o = it
                AcceptanceCheckItem(o.str("criterion"), o.str("result", "NOT_APPLICABLE"), o.str("notes"))
            },
            validation = validation.copy(risks = validation.risks + issues.map { "Schema issue: $it" }),
            followUps = obj.strList("followUps"),
            rawJson = json,
        )
    }

    // --- Review ---
    fun parseReview(raw: String): ReviewArtifact {
        val json = extractJsonBlock(raw) ?: raw
        val obj = parseObject(raw) ?: return ReviewArtifact(
            reviewStatus = "REQUEST_CHANGES",
            summary = "Could not parse review JSON.",
            rawJson = raw
        )
        val sc = obj.obj("scopeCompliance")
        val scope = if (sc != null) ScopeCompliance(
            result = sc.str("result", "PASS"),
            notes = sc.strList("notes"),
        ) else ScopeCompliance()

        val status = oneOf(obj.str("reviewStatus"), setOf("APPROVE", "REQUEST_CHANGES", "REJECT"), "REQUEST_CHANGES")
        val issues = validateRequired(obj, listOf("reviewStatus", "summary", "scopeCompliance", "acceptanceReview", "issues", "recommendedNextAction"))
        return ReviewArtifact(
            reviewStatus = status,
            summary = obj.str("summary").ifBlank {
                if (issues.isEmpty()) "" else "Review output had schema issues: ${issues.joinToString("; ")}"
            },
            scopeCompliance = scope,
            acceptanceReview = obj.array("acceptanceReview").map {
                val o = it
                AcceptanceReviewItem(
                    criterion = o.str("criterion"),
                    result = o.str("result", "UNCLEAR"),
                    evidence = o.strList("evidence"),
                    issues = o.strList("issues"),
                )
            },
            issues = obj.array("issues").map {
                val o = it
                ReviewIssue(
                    severity = o.str("severity", "LOW"),
                    category = o.str("category", "maintainability"),
                    title = o.str("title"),
                    details = o.str("details"),
                    suggestedFix = o.str("suggestedFix"),
                )
            },
            positiveSignals = obj.strList("positiveSignals"),
            recommendedNextAction = obj.str("recommendedNextAction", "revise"),
            followUpChecks = obj.strList("followUpChecks") + issues.map { "Schema issue: $it" },
            rawJson = json,
        )
    }

    /** Serialize arbitrary object to pretty JSON (used for prompt placeholders). */
    fun toJson(any: Any?): String = gson.toJson(any)

    fun planIssues(plan: PlanArtifact): List<String> =
        plan.blockingIssues.filter { it.contains("parse", ignoreCase = true) || it.contains("schema", ignoreCase = true) }

    fun executionIssues(execution: ExecutionArtifact): List<String> =
        buildList {
            if (execution.status == "BLOCKED" && execution.rawJson.isNotBlank() &&
                (execution.summary.contains("parse", true) || execution.summary.contains("schema", true))
            ) {
                add(execution.summary)
            }
            addAll(execution.validation.risks.filter {
                it.contains("out-of-scope", ignoreCase = true) || it.contains("schema", ignoreCase = true)
            })
        }

    fun reviewIssues(review: ReviewArtifact): List<String> =
        buildList {
            if (review.summary.contains("parse", ignoreCase = true) || review.summary.contains("schema", ignoreCase = true)) {
                add(review.summary)
            }
            addAll(review.followUpChecks.filter { it.contains("schema", ignoreCase = true) })
        }

    private fun validateRequired(obj: JsonObject, required: List<String>): List<String> =
        required.filter { !obj.has(it) || obj.get(it).isJsonNull }.map { "missing '$it'" }
}
