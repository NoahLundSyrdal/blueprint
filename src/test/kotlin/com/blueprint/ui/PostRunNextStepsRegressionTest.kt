package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostRunNextStepsRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `run verification success banner includes grounded next steps`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val changedPaths = postApplyInlineSummary?.changedPaths.orEmpty().distinct()"))
        assertTrue(source.contains("0 -> \"Inspect the current code in the IDE if you want to confirm the final state file by file.\""))
        assertTrue(source.contains("1 -> \"Verify in code: Use Open Changed File to inspect \${changedPaths.first()} in the IDE. This does not apply or refresh anything.\""))
        assertTrue(source.contains("else -> \"Verify in code: Use Open Changed Files to inspect the \${changedPaths.size} changed paths in the IDE. This does not apply or refresh anything.\""))
        assertTrue(source.contains("\"Next steps:\""))
        assertTrue(source.contains("\"- Refresh UML From Code again anytime to re-verify the current code-backed UML.\""))
        assertTrue(source.contains("\"- Refine the UML again when you are ready for another reviewed code patch.\""))
        assertTrue(source.contains("appendLine(recordedSteps)"))
        assertTrue(source.contains("appendLine(nextSteps)"))
    }
}
