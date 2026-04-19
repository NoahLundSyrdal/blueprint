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

        assertEquals(
            """
            What changed?
            Plain-English summary after apply:
            - No code changes needed
            
            Blueprint compared the current UML-backed request against the code on disk. Refresh UML From Code to verify the current code, or refine the UML and try a different change.
            
            Changed files: none.
            """.trimIndent(),
            PatchChangeSummary.applySummary(exec, emptyList())
        )
    }

    @Test
    fun `no-op guidance copy explains what Blueprint checked and next steps`() {
        val summary = "No code changes needed. Blueprint compared the current UML-backed request against the code on disk. Refresh UML From Code to verify the current code, or refine the UML and try a different change."

        assertTrue(summary.contains("No code changes needed."))
        assertTrue(summary.contains("current UML-backed request against the code on disk"))
        assertTrue(summary.contains("Refresh UML From Code to verify the current code"))
        assertTrue(summary.contains("refine the UML and try a different change"))
    }
}
