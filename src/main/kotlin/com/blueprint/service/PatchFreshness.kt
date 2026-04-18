package com.blueprint.service

import com.blueprint.model.Patch

data class DiskSnapshot(
    val exists: Boolean,
    val content: String = "",
)

data class PatchFreshnessResult(
    val matchesPatch: Boolean,
    val changedDisk: Boolean,
    val reason: String = "",
)

object PatchFreshness {
    fun verify(patch: Patch, before: DiskSnapshot, after: DiskSnapshot): PatchFreshnessResult {
        val action = patch.action.lowercase().ifBlank { "update" }
        val expected = normalize(if (action == "delete") "" else patch.content)

        if (action == "delete") {
            return when {
                !before.exists ->
                    PatchFreshnessResult(matchesPatch = !after.exists, changedDisk = false, reason = "file already absent")
                after.exists ->
                    PatchFreshnessResult(matchesPatch = false, changedDisk = false, reason = "file still exists after delete")
                else ->
                    PatchFreshnessResult(matchesPatch = true, changedDisk = true)
            }
        }

        val beforeMatches = before.exists && normalize(before.content) == expected
        if (beforeMatches && after.exists && normalize(after.content) == expected) {
            return PatchFreshnessResult(matchesPatch = true, changedDisk = false, reason = "disk already matched patch")
        }
        if (!after.exists) {
            return PatchFreshnessResult(matchesPatch = false, changedDisk = false, reason = "file missing after apply")
        }
        if (normalize(after.content) != expected) {
            return PatchFreshnessResult(matchesPatch = false, changedDisk = false, reason = "disk content did not match applied patch")
        }
        return PatchFreshnessResult(matchesPatch = true, changedDisk = true)
    }

    fun normalize(text: String): String =
        text.replace("\r\n", "\n").trimEnd()
}
