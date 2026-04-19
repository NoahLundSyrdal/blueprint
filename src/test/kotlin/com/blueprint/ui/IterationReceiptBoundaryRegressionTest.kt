package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class IterationReceiptBoundaryRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `activity receipt adds a clear iteration marker only after a completed run`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private var completedRunReceiptCycle = false"))
        assertTrue(source.contains("maybeLogNewIterationBoundary(buttonText)"))
        assertTrue(source.contains("if (!completedRunReceiptCycle || buttonText != \"Generating Code Diff...\") return"))
        assertTrue(source.contains("logActivity(\"New iteration started - returning to Generate Code Diff after a completed run. Iteration \$nextCycle keeps this receipt easy to scan.\")"))
        assertTrue(source.contains("completedRunReceiptCycle = false"))
        assertTrue(source.contains("completedRunReceiptCycle = true"))
        assertTrue(source.contains("activityLog.text.lineSequence().count { it.contains(\"Receipt marker - Iteration \") }"))
        assertTrue(source.contains("msg.replaceFirst(\"New iteration started -\", \"Receipt marker - Iteration \${activityIterationCount() + 1} started -\")"))
    }
}
