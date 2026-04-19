package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ActivityTimelineCoverageRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    /**
     * Verifies the receipt formatter covers the main demo workflow milestones with user-facing wording.
     */
    @Test
    fun `activity receipt keeps the core workflow milestones visible`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("logActivity(\"Abstracted code to UML:"))
        assertTrue(source.contains("logActivity(\"Planning code diff for"))
        assertTrue(source.contains("\"Code diff ready for \${n.title.ifBlank { n.id.take(8) }}: \" +"))
        assertTrue(source.contains("logActivity(\"Apply finished for"))
        assertTrue(source.contains("\"Running validation after apply for \${node.title.ifBlank { node.id.take(8) }}: \$command\""))
        assertTrue(source.contains("\"Validation skipped after apply for \${node.title.ifBlank { node.id.take(8) }}: no command inferred.\""))
        assertTrue(source.contains("\"Freshness verified after apply: refreshed UML from disk with \" +"))
        assertTrue(source.contains("logActivity(\"Opened diff preview for"))
        assertTrue(source.contains("logActivity(\"Opened source for"))
        assertTrue(source.contains("activityLog.append(\"[\$at] \${numbered.toString().padStart(2, '0')}. \${receiptText(msg)}\\n\")"))
    }
}
