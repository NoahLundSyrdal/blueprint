package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class WorkflowCopyRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `default chat guidance avoids node by node workflow copy`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("click Generate Code Diff when you are ready for a reviewed code patch"))
        assertTrue(source.contains("is ready. Keep refining the UML if needed, then click Generate Code Diff."))
        assertFalse(source.contains("Run Generate Plan -> Execute Node -> Review -> Preview Diff -> Apply All."))
        assertFalse(source.contains("is ready. Run Generate Plan, then Execute Node."))
    }
}
