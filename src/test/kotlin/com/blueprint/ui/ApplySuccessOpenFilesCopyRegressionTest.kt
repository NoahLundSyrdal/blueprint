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
        assertTrue(source.contains("1 -> \"Optional after verification: Use Open Changed File if you want to inspect exactly what Blueprint wrote.\""))
        assertTrue(source.contains("else -> \"Optional after verification: Use Open Changed Files if you want to inspect exactly what Blueprint wrote.\""))
        assertTrue(source.contains("listOf(nextActionLine, summaryLine, whatChanged, changedPathsBlock, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, verifyStateLine, highlightLine, validationBlock)"))
        assertTrue(source.contains("+ if (openChangedFilesNote.isBlank()) \"\" else \"\\n\\n\$openChangedFilesNote\""))
        assertTrue(source.contains("\"Blueprint - Apply Complete\""))
    }
}
