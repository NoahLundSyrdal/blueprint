package com.blueprint.service

data class UndoEntry(
    val path: String,
    val before: DiskSnapshot,    // disk state captured before the patch was applied
    val patchAction: String,     // what was applied: create | update | delete
    val patchContent: String,    // what was written (used for staleness detection)
)

data class UndoRecord(
    val nodeTitle: String,
    val entries: List<UndoEntry>,
    val appliedAt: Long = System.currentTimeMillis(),
)

data class UndoResult(
    val restored: List<String>,
    val skipped: List<Pair<String, String>>,  // path → reason
)

sealed class UndoDecision {
    /** Write before.content back (or recreate the file if the patch deleted it). */
    data class RestoreContent(val content: String) : UndoDecision()
    /** Delete the file — it was created by the patch and did not exist before. */
    object DeleteFile : UndoDecision()
    /** Do nothing — file was modified after apply or is in an unexpected state. */
    data class Skip(val reason: String) : UndoDecision()
}

object UndoFreshness {
    /**
     * Pure decision function — no VFS, no disk access.
     * Determines what undo should do to a single file.
     */
    fun decide(entry: UndoEntry, current: DiskSnapshot): UndoDecision {
        val action = entry.patchAction.lowercase().ifBlank { "update" }

        if (action == "delete") {
            // The patch deleted this file. The correct pre-apply state is the file existing.
            // If it somehow came back (user recreated it) we can't know its provenance — skip.
            if (current.exists) return UndoDecision.Skip(
                "file was recreated after apply — restore manually to avoid data loss"
            )
            if (!entry.before.exists) return UndoDecision.Skip(
                "no before-snapshot to restore (file did not exist before apply)"
            )
            return UndoDecision.RestoreContent(entry.before.content)
        }

        // create or update: the patch wrote content to disk.
        // Staleness check: if the file no longer holds what Blueprint wrote, the user touched it.
        if (!current.exists) return UndoDecision.Skip(
            "file is missing — may have been deleted manually after apply"
        )
        if (PatchFreshness.normalize(current.content) != PatchFreshness.normalize(entry.patchContent)) {
            return UndoDecision.Skip(
                "file was modified after apply — restore manually to avoid data loss"
            )
        }

        // File still contains exactly what Blueprint wrote — safe to reverse.
        return if (entry.before.exists) {
            UndoDecision.RestoreContent(entry.before.content)
        } else {
            // The patch created this file; before it did not exist. Undo = delete it.
            UndoDecision.DeleteFile
        }
    }
}
