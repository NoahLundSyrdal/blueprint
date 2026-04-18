package com.blueprint.ui

import com.blueprint.model.BlueprintNode
import com.blueprint.model.ExecutionArtifact
import com.blueprint.service.ApplyChangesService
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

object DiffPreview {

    /** Opens the JetBrains diff viewer with one tab per patched file. */
    fun show(project: Project, node: BlueprintNode, exec: ExecutionArtifact) {
        if (exec.patches.isEmpty()) return
        val cf = DiffContentFactory.getInstance()
        val apply = project.service<ApplyChangesService>()

        val requests = exec.patches.map { p ->
            val before = apply.readCurrentContent(p.path)
            val after = when (p.action.lowercase()) {
                "delete" -> ""
                else -> p.content
            }
            val left = cf.create(project, before)
            val right = cf.create(project, after)
            val title = when (p.action.lowercase()) {
                "create" -> "CREATE ${p.path}"
                "delete" -> "DELETE ${p.path}"
                else -> "UPDATE ${p.path}"
            }
            SimpleDiffRequest(title, left, right, "Current", "Proposed")
        }

        DiffManager.getInstance().showDiff(
            project,
            SimpleDiffRequestChain(requests),
            com.intellij.diff.DiffDialogHints.DEFAULT
        )
    }
}
