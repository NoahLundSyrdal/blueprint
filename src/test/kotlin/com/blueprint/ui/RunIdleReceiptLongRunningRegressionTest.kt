package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RunIdleReceiptLongRunningRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `idle run receipt warns that inferred dev servers may keep streaming until stopped`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("Run In Blueprint will launch: \$it"))
        assertTrue(source.contains("Click Run In Blueprint to start: \$it"))
        assertTrue(source.contains("If this command starts a dev server or watcher, it may keep streaming until you click Stop Run."))
    }

    @Test
    fun `idle run receipt keeps the no command fallback guidance`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("${'$'}{runner.noCommandSummary(context)}"))
        assertTrue(source.contains("When a command is available, Run In Blueprint will stream stdout and stderr here."))
    }
}
