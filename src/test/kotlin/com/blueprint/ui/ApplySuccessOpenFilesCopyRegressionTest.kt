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
        assertTrue(source.contains("1 -> \"Optional: Open Changed File to inspect what Blueprint wrote after you verify the refreshed UML and rerun the app.\""))
        assertTrue(source.contains("else -> \"Optional: Open Changed Files to inspect what Blueprint wrote after you verify the refreshed UML and rerun the app.\""))
        assertTrue(source.contains("listOf(summaryLine, whatChanged, changedPathsBlock, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, highlightLine, validationBlock)"))
        assertTrue(source.contains("+ if (openChangedFilesNote.isBlank()) \"\" else \"\\n\\n\$openChangedFilesNote\""))
        assertTrue(source.contains("\"Blueprint - Apply Complete\""))
    }
}
