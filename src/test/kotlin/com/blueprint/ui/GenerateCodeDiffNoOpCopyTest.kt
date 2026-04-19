package com.blueprint.ui

import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.Patch
import com.blueprint.model.PatchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateCodeDiffNoOpCopyTest {
    @Test
    fun `review summary for missing patch stays user friendly`() {
        assertEquals("What changed?\n- No reviewed code patch yet.", PatchChangeSummary.reviewSummary(null))
    }

    @Test
    fun `apply summary for no applied paths is explicit`() {
        val exec = ExecutionArtifact(
            summary = "No-op after review.",
            patches = listOf(
                Patch(path = "app/models.py", action = PatchAction.update.name, content = "class Invite"),
            ),
        )

        assertEquals("What changed?\n- No code changes needed", PatchChangeSummary.applySummary(exec, emptyList()))
    }

    @Test
    fun `no-op guidance copy explains next steps`() {
        val checked = 2
        val nextStep = "Refine the UML, or click Refresh UML From Code to verify the current code before trying a different change."
        val summary = "No code changes needed. The UML already appears to match the current code for $checked checked node(s). $nextStep"

        assertTrue(summary.contains("No code changes needed."))
        assertTrue(summary.contains("Refresh UML From Code to verify the current code"))
        assertTrue(summary.contains("trying a different change"))
    }
}
