package com.blueprint.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * File scope: explicit allowlist of paths/globs/directories that a node may touch.
 * Matches the intent of graph_node_definition_prompt (fileScope section).
 */
data class FileScope(
    val paths: List<String> = emptyList(),      // exact files, e.g. src/foo/bar.kt
    val globs: List<String> = emptyList(),      // e.g. src/features/invites/**
    val directories: List<String> = emptyList() // e.g. src/server/routes
) {
    fun isEmpty(): Boolean = paths.isEmpty() && globs.isEmpty() && directories.isEmpty()

    /** True iff [relPath] (project-relative, forward slashes) is allowed by this scope. */
    fun allows(relPath: String): Boolean {
        val p = normalizeRelativePath(relPath) ?: return false
        if (paths.any { normalizeRelativePath(it) == p }) return true
        if (directories.any { d ->
                val dd = normalizeRelativePath(d)?.trimEnd('/') ?: return@any false
                p == dd || p.startsWith("$dd/")
            }) return true
        if (globs.any { matchesGlob(normalizeGlob(it), p) }) return true
        return false
    }

    fun allowedRoots(): List<String> =
        (paths + directories).mapNotNull { normalizeRelativePath(it) }.distinct()

    private fun matchesGlob(glob: String, path: String): Boolean {
        // Minimal glob: **, *, ?
        val regex = buildString {
            append('^')
            var i = 0
            while (i < glob.length) {
                val c = glob[i]
                when {
                    c == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> { append(".*"); i += 2 }
                    c == '*' -> { append("[^/]*"); i++ }
                    c == '?' -> { append("[^/]"); i++ }
                    "\\.+()[]{}|^$".contains(c) -> { append('\\').append(c); i++ }
                    else -> { append(c); i++ }
                }
            }
            append('$')
        }.toRegex()
        return regex.matches(path)
    }

    private fun normalizeGlob(glob: String): String =
        glob.replace('\\', '/').trim().trimStart('/')

    companion object {
        fun normalizeRelativePath(raw: String): String? {
            val p = raw.replace('\\', '/').trim()
            if (p.isBlank()) return null
            if (Regex("^[A-Za-z]:").containsMatchIn(p)) return null
            if (p.startsWith("~")) return null
            val parts = p.trimStart('/').split('/').filter { it.isNotBlank() && it != "." }
            if (parts.any { it == ".." }) return null
            return parts.joinToString("/")
        }
    }
}

enum class ExecutionStatus {
    @SerializedName("draft")
    DRAFT,
    @SerializedName("planned")
    PLANNED,
    @SerializedName("executing")
    EXECUTING,
    @SerializedName("review")
    REVIEW,
    @SerializedName("applied")
    APPLIED,
    @SerializedName("blocked")
    BLOCKED,
    @SerializedName("failed")
    FAILED
}

enum class NodeType {
    @SerializedName("schema")
    SCHEMA,
    @SerializedName("backend")
    BACKEND,
    @SerializedName("frontend")
    FRONTEND,
    @SerializedName("test")
    TEST,
    @SerializedName("migration")
    MIGRATION,
    @SerializedName("telemetry")
    TELEMETRY,
    @SerializedName("docs")
    DOCS,
    @SerializedName("other")
    OTHER
}

enum class AcceptanceCriterionType {
    @SerializedName("codegen")
    CODEGEN,
    @SerializedName("interface_contract")
    INTERFACE_CONTRACT,
    @SerializedName("test")
    TEST,
    @SerializedName("ux")
    UX,
    @SerializedName("non_functional")
    NON_FUNCTIONAL,
    @SerializedName("other")
    OTHER
}

data class AcceptanceCriterion(
    val id: String = UUID.randomUUID().toString(),
    val type: AcceptanceCriterionType = AcceptanceCriterionType.OTHER,
    val description: String = "",
    val verifyWith: String = "",
    val required: Boolean = true
)

data class NodeContract(
    val name: String = "",
    val kind: String = "",          // api|component|schema|event|file|other
    val description: String = "",
    val schema: String = ""
)

/**
 * Canonical in-memory node. Mirrors the persisted JSON shape from
 * graph_node_definition_prompt. Fields kept loose for MVP (String JSON
 * fragments for generated artifacts are stored alongside as artifacts).
 */
data class BlueprintNode(
    val id: String = UUID.randomUUID().toString(),
    var type: NodeType = NodeType.BACKEND,
    var title: String = "",
    var summary: String = "",
    var description: String = "",
    var inputs: List<NodeContract> = emptyList(),
    var outputs: List<NodeContract> = emptyList(),
    var dependencies: List<String> = emptyList(), // node ids
    var fileScope: FileScope = FileScope(),
    var acceptanceCriteria: List<AcceptanceCriterion> = emptyList(),
    var invariants: List<String> = emptyList(),
    var riskLevel: String = "LOW",
    var executionStatus: ExecutionStatus = ExecutionStatus.DRAFT,
    var metadata: MutableMap<String, String> = mutableMapOf()
)
