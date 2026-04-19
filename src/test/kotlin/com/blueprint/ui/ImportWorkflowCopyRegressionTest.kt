package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ImportWorkflowCopyRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    /**
     * Verifies imported UML guidance keeps advanced node terminology out of the default flow.
     */
    @Test
    fun `import success copy stays on editable uml and code diff workflow`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("into an editable UML draft. Review it, then click Generate Code Diff to preview code changes."))
        assertFalse(source.contains("generated node(s). Click Generate Code Diff to preview code changes."))
    }
}
