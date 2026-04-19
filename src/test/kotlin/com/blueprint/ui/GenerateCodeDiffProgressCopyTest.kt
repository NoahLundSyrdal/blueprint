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
    fun `no op review summary says uml already matches code`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("No code changes: UML already matches code."))
        assertTrue(source.contains("No code changes: UML already matches code for"))
    }
}
