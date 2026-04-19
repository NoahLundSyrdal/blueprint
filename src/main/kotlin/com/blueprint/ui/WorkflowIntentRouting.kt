package com.blueprint.ui

internal object WorkflowIntentRouting {
    private val workflowKeywords = listOf(
        "run",
        "next",
        "why",
        "blocked",
        "diff",
        "changed",
        "generate code",
        "generate patch",
        "create patch",
        "apply patch",
        "refresh from code",
        "code nodes",
        "create code nodes",
        "apply",
    )

    /**
     * Returns true when the message is asking about the default Blueprint workflow
     * instead of requesting a UML edit.
     */
    fun isWorkflowQuestion(message: String): Boolean =
        workflowKeywords.any { keyword -> keyword in message.lowercase() }
}
