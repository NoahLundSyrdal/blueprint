package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RefreshUmlVerificationCopyRegressionTest {
    @Test
    fun `post apply verification copy uses refresh uml from code wording consistently`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("toolTipText = POST_APPLY_VERIFY_TOOLTIP"))
        assertTrue(source.contains("appendLine(POST_APPLY_VERIFY_HEADING)"))
        assertTrue(source.contains("appendLine(\"- Click \$POST_APPLY_VERIFY_PROMPT\")"))
        assertTrue(source.contains("private const val POST_APPLY_NEXT_STEP_LINE = \"Next: Refresh UML From Code to verify the updated code-backed UML.\""))
    }
}
