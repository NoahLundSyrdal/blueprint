package com.blueprint.ui

import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.Patch
import com.blueprint.model.PatchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchChangeSummaryClassActionTest {
    @Test
    fun `review summary calls out added classes directly`() {
        val exec = ExecutionArtifact(
            summary = "Create InvitePolicy.",
            patches = listOf(
                Patch(
                    path = "app/policy.py",
                    action = PatchAction.create.name,
                    content = """
                        class InvitePolicy:
                            name: str
                    """.trimIndent(),
                ),
            ),
        )

        val summary = PatchChangeSummary.reviewSummary(exec)

        assertTrue(summary.contains("Added class InvitePolicy"))
        assertTrue(summary.contains("InvitePolicy + name: str"))
    }

    @Test
    fun `semantic change lines call out updated classes when only the class header changes`() {
        val exec = ExecutionArtifact(
            summary = "Update Invite.",
            patches = listOf(
                Patch(
                    path = "app/models.py",
                    action = PatchAction.update.name,
                    content = """
                        --- a/app/models.py
                        +++ b/app/models.py
                        @@
                        -class Invite:
                        +class Invite:
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(listOf("Updated class Invite"), PatchChangeSummary.semanticChangeLines(exec))
    }
}
