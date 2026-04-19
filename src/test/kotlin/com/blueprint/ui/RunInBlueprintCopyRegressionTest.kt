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
        assertTrue(source.contains("Blueprint is still streaming output. Long-running apps can stay here until you click Stop Run."))
    }

    @Test
    fun `run guidance now mentions the in app runner fallback`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("When you want to run the app, start with: \$it or click Run In Blueprint."))
        assertTrue(source.contains("Run the changed app with: \$it, or click Run In Blueprint to stream it here."))
    }

    @Test
    fun `run output area starts with a clear idle receipt for inferred and missing commands`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("text = runOutputIdleHint()"))
        assertTrue(source.contains("Click Run In Blueprint to start: \$it"))
        assertTrue(source.contains("Blueprint will stream stdout and stderr here."))
        assertTrue(source.contains("When a command is available, Run In Blueprint will stream stdout and stderr here."))
        assertTrue(source.contains("if (runOutputArea.text.isBlank() || runOutputArea.text == \"Preparing inferred run command...\")"))
    }
}
