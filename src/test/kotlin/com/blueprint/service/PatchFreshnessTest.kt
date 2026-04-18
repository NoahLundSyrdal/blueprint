package com.blueprint.service

import com.blueprint.model.Patch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchFreshnessTest {
    @Test
    fun `update must match patch and actually change disk`() {
        val patch = Patch(path = "models.py", action = "update", content = "class Invite:\n    pass\n")

        val changed = PatchFreshness.verify(
            patch,
            before = DiskSnapshot(exists = true, content = "class Invite:\n    id: str\n"),
            after = DiskSnapshot(exists = true, content = "class Invite:\n    pass\n"),
        )
        assertTrue(changed.matchesPatch)
        assertTrue(changed.changedDisk)

        val stale = PatchFreshness.verify(
            patch,
            before = DiskSnapshot(exists = true, content = "class Invite:\n    pass\n"),
            after = DiskSnapshot(exists = true, content = "class Invite:\n    pass\n"),
        )
        assertTrue(stale.matchesPatch)
        assertFalse(stale.changedDisk)
        assertTrue(stale.reason.contains("already matched"))
    }

    @Test
    fun `create and delete account for file existence not just content`() {
        val createEmpty = Patch(path = "empty.py", action = "create", content = "")
        val created = PatchFreshness.verify(
            createEmpty,
            before = DiskSnapshot(exists = false),
            after = DiskSnapshot(exists = true, content = ""),
        )
        assertTrue(created.matchesPatch)
        assertTrue(created.changedDisk)

        val deleteEmpty = Patch(path = "empty.py", action = "delete", content = "")
        val deleted = PatchFreshness.verify(
            deleteEmpty,
            before = DiskSnapshot(exists = true, content = ""),
            after = DiskSnapshot(exists = false),
        )
        assertTrue(deleted.matchesPatch)
        assertTrue(deleted.changedDisk)

        val alreadyAbsent = PatchFreshness.verify(
            deleteEmpty,
            before = DiskSnapshot(exists = false),
            after = DiskSnapshot(exists = false),
        )
        assertTrue(alreadyAbsent.matchesPatch)
        assertFalse(alreadyAbsent.changedDisk)
    }
}
