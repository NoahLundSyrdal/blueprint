package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyNextStepPriorityRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `post apply guidance prioritizes verify and run before file inspection`() {
        assertTrue(source.contains("Refresh UML From Code to verify the updated code-backed UML."))
        assertTrue(source.contains("- Run the changed app to confirm the feature exists."))
        assertTrue(source.contains("- Review the changed paths, validation result, and inferred run command above."))
        assertTrue(source.contains("- Open Changed Files is optional after verification if you want to inspect what Blueprint wrote."))
        assertFalse(source.contains("Open Changed Files to inspect what Blueprint wrote before you rerun the app."))
    }

    @Test
    fun `all applied next step banner uses product language instead of re abstract`() {
        assertTrue(source.contains("\"Next: Refresh UML From Code\" to \"All current work is applied. Refresh UML From Code to verify the updated code-backed UML, then run the changed app or make another change.\""))
        assertFalse(source.contains("All current work is applied. Re-abstract the updated codebase."))
    }
}
