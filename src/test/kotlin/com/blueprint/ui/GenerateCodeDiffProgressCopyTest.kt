package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class GenerateCodeDiffProgressCopyTest {
    @Test
    fun `generate code diff progress mentions the selected node`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("Generating Code Diff for "))
        assertTrue(source.contains("Blueprint will plan, write a scoped patch, review it, and open the diff."))
    }

    @Test
    fun `no op review summary says what Blueprint checked`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("No code changes needed. Blueprint compared the current UML-backed request against the code on disk. Refresh UML From Code to verify the current code, or refine the UML and try a different change."))
        assertTrue(source.contains("private fun noOpDiffMessage(): String ="))
    }

    @Test
    fun `no op activity and chat use matching reason`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("appendChat(\"Blueprint\", noOpMessage)"))
        assertTrue(source.contains("Generate Code Diff found no file changes because the current UML-backed request already matched the code on disk."))
        assertTrue(source.contains("No file changes were needed for "))
        assertTrue(source.contains("because that UML-backed request already matched the code on disk."))
    }
}
