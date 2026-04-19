package com.blueprint.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoFreshnessTest {

    @Test
    fun `restore updated file when disk still matches what was written`() {
        val entry = UndoEntry(
            path = "app/models.py",
            before = DiskSnapshot(exists = true, content = "class Invite:\n    id: str\n"),
            patchAction = "update",
            patchContent = "class Invite:\n    id: str\n    name: str\n",
        )
        val current = DiskSnapshot(exists = true, content = "class Invite:\n    id: str\n    name: str\n")

        val decision = UndoFreshness.decide(entry, current)

        assertTrue(decision is UndoDecision.RestoreContent)
        assertEquals(entry.before.content, (decision as UndoDecision.RestoreContent).content)
    }

    @Test
    fun `refuse restore when file was edited after apply`() {
        val entry = UndoEntry(
            path = "app/models.py",
            before = DiskSnapshot(exists = true, content = "class Invite:\n    id: str\n"),
            patchAction = "update",
            patchContent = "class Invite:\n    id: str\n    name: str\n",
        )
        // User edited the file after Blueprint applied — content no longer matches patch
        val current = DiskSnapshot(exists = true, content = "class Invite:\n    id: str\n    name: str\n    city: str\n")

        val decision = UndoFreshness.decide(entry, current)

        assertTrue(decision is UndoDecision.Skip)
        assertTrue((decision as UndoDecision.Skip).reason.contains("modified after apply"))
    }

    @Test
    fun `remove created file on undo`() {
        val entry = UndoEntry(
            path = "app/new_service.py",
            before = DiskSnapshot(exists = false),   // file did not exist before
            patchAction = "create",
            patchContent = "class NewService:\n    pass\n",
        )
        // File is on disk exactly as Blueprint wrote it
        val current = DiskSnapshot(exists = true, content = "class NewService:\n    pass\n")

        val decision = UndoFreshness.decide(entry, current)

        assertTrue(decision is UndoDecision.DeleteFile)
    }

    @Test
    fun `restore deleted file on undo`() {
        val entry = UndoEntry(
            path = "app/old_service.py",
            before = DiskSnapshot(exists = true, content = "class OldService:\n    pass\n"),
            patchAction = "delete",
            patchContent = "",
        )
        // File is absent — the delete landed correctly
        val current = DiskSnapshot(exists = false)

        val decision = UndoFreshness.decide(entry, current)

        assertTrue(decision is UndoDecision.RestoreContent)
        assertEquals(entry.before.content, (decision as UndoDecision.RestoreContent).content)
    }
}
