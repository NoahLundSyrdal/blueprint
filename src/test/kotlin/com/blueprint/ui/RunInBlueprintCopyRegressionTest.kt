package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RunInBlueprintCopyRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `run panel exposes in app run action stream and stop copy`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private val runAppButton = JButton(\"Run In Blueprint\")"))
        assertTrue(source.contains("border = BorderFactory.createTitledBorder(\"Run Output\")"))
        assertTrue(source.contains("runAppButton.text = \"Stop Run\""))
        assertTrue(source.contains("runAppButton.toolTipText = \"Stop the inferred project command running inside Blueprint.\""))
        assertTrue(source.contains("Run and stream output for:"))
        assertTrue(source.contains("toggleRunInBlueprint()"))
        assertTrue(source.contains("runOutputArea.text = \"Preparing inferred run command...\""))
        assertTrue(source.contains("Run stopped from Blueprint."))
    }

    @Test
    fun `run guidance now mentions the in app runner fallback`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("When you want to run the app, start with: \$it or click Run In Blueprint."))
        assertTrue(source.contains("Run the changed app with: \$it, or click Run In Blueprint to stream it here."))
    }
}
