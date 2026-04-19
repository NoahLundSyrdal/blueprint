package com.blueprint.ui

import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.Patch
import com.blueprint.model.PatchAction
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchChangeSummaryModuleChangeTest {
    @Test
    fun `review summary calls out module-level field style edits before apply`() {
        val exec = ExecutionArtifact(
            summary = "Update package exports.",
            patches = listOf(
                Patch(
                    path = "app/__init__.py",
                    action = PatchAction.update.name,
                    content = """
                        from .models import Invite
                        __all__: list[str] = ['Invite']
                    """.trimIndent(),
                ),
            ),
        )

        val summary = PatchChangeSummary.reviewSummary(exec)

        assertTrue(summary.contains("Plain-English summary before apply:"))
        assertTrue(summary.contains("__init__.py + __all__: list[str]"))
    }

    @Test
    fun `apply summary keeps only applied module-level changes in multi-file diffs`() {
        val exec = ExecutionArtifact(
            summary = "Update exports and add a policy.",
            patches = listOf(
                Patch(
                    path = "app/__init__.py",
                    action = PatchAction.update.name,
                    content = "__all__: list[str] = ['Invite', 'InvitePolicy']",
                ),
                Patch(
                    path = "app/policy.py",
                    action = PatchAction.create.name,
                    content = "class InvitePolicy:\n    id: str",
                ),
            ),
        )

        val summary = PatchChangeSummary.applySummary(exec, listOf("app/__init__.py"))

        assertTrue(summary.contains("Plain-English summary after apply:"))
        assertTrue(summary.contains("__init__.py + __all__: list[str]"))
        assertTrue(!summary.contains("InvitePolicy + id: str"))
    }
}
