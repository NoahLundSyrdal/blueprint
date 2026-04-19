package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ApplySuccessOpenFilesCopyRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `apply success dialog mentions open changed file action for one and many files`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val openChangedFilesNote = when (changedPaths.size) {"))
        assertTrue(source.contains("1 -> \"Verify in code: Use Open Changed File to inspect the primary changed file in the IDE. This does not apply or refresh anything.\""))
        assertTrue(source.contains("else -> \"Verify in code: Use Open Changed Files to inspect the primary changed files in the IDE. This does not apply or refresh anything.\""))
        assertTrue(source.contains("listOf(nextActionLine, summaryLine, whatChanged, changedPathsBlock, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, verifyStateLine, highlightLine, validationBlock)"))
        assertTrue(source.contains("+ if (openChangedFilesNote.isBlank()) \"\" else \"\\n\\n\$openChangedFilesNote\""))
        assertTrue(source.contains("\"Blueprint - Apply Complete\""))
    }
}
