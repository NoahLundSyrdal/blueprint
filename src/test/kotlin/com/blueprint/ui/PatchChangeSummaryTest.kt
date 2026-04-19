package com.blueprint.ui

import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.Patch
import com.blueprint.model.PatchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchChangeSummaryTest {
    @Test
    fun `review summary includes semantic changes and changed files`() {
        val exec = ExecutionArtifact(
            summary = "Add invite policy support.",
            patches = listOf(
                Patch(
                    path = "blueprint_demo/imported_invite/models.py",
                    action = PatchAction.update.name,
                    content = """
                        from dataclasses import dataclass

                        @dataclass
                        class Invite:
                            accepted_at: datetime | None = None

                        class InviteAuditLog:
                            actor_ip: str
                    """.trimIndent(),
                ),
            ),
        )

        val summary = PatchChangeSummary.reviewSummary(exec)

        assertTrue(summary.contains("What changed?"))
        assertTrue(summary.contains("Plain-English summary before apply:"))
        assertTrue(summary.contains("Invite + accepted_at: datetime | None"))
        assertTrue(summary.contains("InviteAuditLog + actor_ip: str"))
        assertTrue(summary.contains("Changed files (1):"))
        assertTrue(summary.contains("- update blueprint_demo/imported_invite/models.py"))
    }

    @Test
    fun `review summary lists create update and delete actions`() {
        val exec = ExecutionArtifact(
            summary = "Update invite files.",
            patches = listOf(
                Patch(path = "app/new_models.py", action = PatchAction.create.name, content = "class InvitePolicy:\n    id: str"),
                Patch(path = "app/models.py", action = PatchAction.update.name, content = "class Invite:\n    accepted_at: datetime"),
                Patch(path = "app/old_models.py", action = PatchAction.delete.name, content = ""),
            ),
        )

        val summary = PatchChangeSummary.reviewSummary(exec)

        assertTrue(summary.contains("- create app/new_models.py"))
        assertTrue(summary.contains("- update app/models.py"))
        assertTrue(summary.contains("- delete app/old_models.py"))
    }

    @Test
    fun `apply summary filters to applied paths and falls back cleanly`() {
        val exec = ExecutionArtifact(
            summary = "No-op after review.",
            patches = listOf(
                Patch(path = "app/models.py", action = PatchAction.update.name, content = "class Invite:\n    accepted_at: datetime"),
                Patch(path = "app/unused.py", action = PatchAction.delete.name, content = ""),
            ),
        )

        val summary = PatchChangeSummary.applySummary(exec, listOf("app/models.py"))

        assertTrue(summary.contains("What changed:"))
        assertTrue(summary.contains("Invite + accepted_at: datetime"))
        assertTrue(summary.contains("Changed files (1):"))
        assertTrue(summary.contains("- update app/models.py"))
        assertTrue(!summary.contains("unused.py"))
        assertEquals("No code changes needed", PatchChangeSummary.applySummary(exec, emptyList()))
    }


    @Test
    fun `apply summary reuses semantic lines after apply`() {
        val exec = ExecutionArtifact(
            summary = "Applied invite updates.",
            patches = listOf(
                Patch(
                    path = "app/models.py",
                    action = PatchAction.update.name,
                    content = """
                        class Invite:
                            accepted_at: datetime | None = None

                        class InviteAuditLog:
                            actor_ip: str
                    """.trimIndent(),
                ),
            ),
        )

        val summary = PatchChangeSummary.applySummary(exec, listOf("app/models.py"))

        assertTrue(summary.contains("What changed:"))
        assertTrue(summary.contains("Invite + accepted_at: datetime | None"))
        assertTrue(summary.contains("InviteAuditLog + actor_ip: str"))
        assertTrue(summary.contains("Changed files (1):"))
        assertTrue(summary.contains("- update app/models.py"))
    }

    @Test
    fun `apply summary ignores paths that are not part of the reviewed diff`() {
        val exec = ExecutionArtifact(
            summary = "Reviewed patch only touched app/models.py.",
            patches = listOf(
                Patch(path = "app/models.py", action = PatchAction.update.name, content = "class Invite:\n    accepted_at: datetime"),
            ),
        )

        assertEquals("No code changes needed", PatchChangeSummary.applySummary(exec, listOf("app/other.py")))
    }

    @Test
    fun `review summary handles empty and missing execution states`() {
        assertEquals("What changed?\n- No reviewed code patch yet.", PatchChangeSummary.reviewSummary(null))
        assertEquals(
            "What changed?\n- No code changes needed",
            PatchChangeSummary.reviewSummary(ExecutionArtifact(patches = emptyList())),
        )
        assertEquals("Changed files: none yet.", PatchChangeSummary.changedFilesSummary(null))
        assertEquals("Changed files: none.", PatchChangeSummary.changedFilesSummary(ExecutionArtifact(patches = emptyList())))
    }
}
