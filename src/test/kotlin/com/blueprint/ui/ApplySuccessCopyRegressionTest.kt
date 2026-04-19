package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ApplySuccessCopyRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `apply success message keeps verification steps clear and compile safe`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val changedPathsBlock = buildString {"))
        assertTrue(source.contains("appendLine(\"Changed paths:\")"))
        assertTrue(source.contains("changedPaths.forEach { appendLine(\"- \$it\") }"))
        assertTrue(source.contains("val validationBlock = buildString {"))
        assertTrue(source.contains("appendLine(\"Validation:\")"))
        assertTrue(source.contains("appendLine(validationReportText(result))"))
        assertTrue(source.contains("listOf(summaryLine, whatChanged, changedPathsBlock, refreshNote, umlRefreshLine, validationBlock)"))
        assertTrue(source.contains("status(summaryLine)"))
        assertTrue(source.contains("appendChat(\"Blueprint\", \"\$summaryLine\\n\$whatChanged\\n\$refreshNote\\n\$umlRefreshLine\")"))
    }
}
