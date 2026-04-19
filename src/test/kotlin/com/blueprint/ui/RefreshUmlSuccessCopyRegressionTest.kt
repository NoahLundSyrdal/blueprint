package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RefreshUmlSuccessCopyRegressionTest {
    private val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

    @Test
    fun `first refresh copy describes initial load of current folder`() {
        assertTrue(source.contains("Current Python folder is already loaded into the code-backed UML. Refresh again anytime if the code on disk changes."))
        assertTrue(source.contains("Load the current Python folder into a code-backed UML diagram for the first time."))
    }

    @Test
    fun `post apply refresh copy describes verification instead of first load`() {
        assertTrue(source.contains("postApplyVerifyState = \"Blueprint reran Refresh UML From Code after apply and verified the latest code-backed UML.\""))
        assertTrue(source.contains("logActivity(\"Refresh UML From Code reran after apply and verified the changed code-backed UML from disk.\")"))
        assertTrue(source.contains("status(\"Verifying updated code-backed UML after apply\")"))
    }
}
