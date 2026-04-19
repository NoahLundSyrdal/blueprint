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
    fun `no op review summary says no code changes needed`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("No code changes needed. The UML already appears to match the current code. Refresh UML From Code to verify the current code, or refine the UML and try a different change."))
        assertTrue(source.contains("No code changes needed. The UML already appears to match the current code for"))
    }

    @Test
    fun `no op activity and chat make the reason explicit`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("val noOpMessage = if (checked == 0)"))
        assertTrue(source.contains("appendChat(\"Blueprint\", noOpMessage)"))
        assertTrue(source.contains("Generate Code Diff completed with no code changes because the UML already matched the current code across"))
    }
}
