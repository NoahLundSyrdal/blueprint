package com.blueprint.ui

import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.Patch
import com.blueprint.model.PatchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchChangeSummaryBehaviorTest {
    @Test
    fun `semantic change lines honor changed path filter`() {
        val exec = ExecutionArtifact(
            summary = "Update invite code.",
            patches = listOf(
                Patch(path = "app/models.py", action = PatchAction.update.name, content = "class Invite:\n    accepted_at: datetime"),
                Patch(path = "app/policy.py", action = PatchAction.create.name, content = "class InvitePolicy:\n    name: str"),
            ),
        )

        assertEquals(
            listOf("InvitePolicy + name: str"),
            PatchChangeSummary.semanticChangeLines(exec, listOf("app/policy.py")),
        )
    }

    @Test
    fun `field summary ignores return annotations and malformed lines`() {
        val method = PatchChangeSummary::class.java.getDeclaredMethod("fieldSummary", String::class.java)
        method.isAccessible = true

        assertEquals(null, method.invoke(PatchChangeSummary, "return: Invite"))
        assertEquals(null, method.invoke(PatchChangeSummary, "not a field"))
        assertEquals("accepted_at: datetime | None", method.invoke(PatchChangeSummary, "accepted_at: datetime | None = None"))
    }

    @Test
    fun `review summary falls back to patch summary when no classes are present`() {
        val exec = ExecutionArtifact(
            summary = "Touched module exports.",
            patches = listOf(
                Patch(path = "app/__init__.py", action = PatchAction.update.name, content = "from .models import Invite\n__all__ = ['Invite']"),
            ),
        )

        val summary = PatchChangeSummary.reviewSummary(exec)

        assertTrue(summary.contains("__init__.py updated"))
    }

    @Test
    fun `apply summary falls back to patch summary when filtered patch has no semantic details`() {
        val exec = ExecutionArtifact(
            summary = "Updated package exports.",
            patches = listOf(
                Patch(path = "app/__init__.py", action = PatchAction.update.name, content = "from .models import Invite\n__all__ = ['Invite']"),
            ),
        )

        val summary = PatchChangeSummary.applySummary(exec, listOf("app/__init__.py"))

        assertTrue(summary.contains("__init__.py updated"))
    }
}
