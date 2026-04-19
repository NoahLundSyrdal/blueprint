package com.blueprint.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GuidedInviteResetRegressionTest {
    @Test
    fun `guided invite reset baseline helpers restore the sandbox file`() {
        val tempDir = Files.createTempDirectory("guided-invite-reset").toFile()
        val target = tempDir.resolve(GuidedInviteScenario.PATCH_PATH)
        target.parentFile.mkdirs()
        target.writeText("changed")

        assertTrue(GuidedInviteScenario.needsReset(tempDir.path))
        assertTrue(GuidedInviteScenario.resetImportedInviteFile(tempDir.path))
        assertFalse(GuidedInviteScenario.needsReset(tempDir.path))
        assertEquals(GuidedInviteScenario.baselineText(), target.readText())
    }

    @Test
    fun `ui source keeps one click reset copy discoverable`() {
        val source = java.nio.file.Files.readString(java.nio.file.Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("GuidedInviteScenario.resetImportedInviteFile(project.basePath)"))
        assertTrue(source.contains("\"Reset Demo Sandbox\""))
        assertTrue(source.contains("\"Invite demo sandbox reset\""))
        assertTrue(source.contains("Reset Demo Sandbox restored \\${'$'}{state.resetPath} to the baseline invite demo file and removed the guided change that was already there. Refresh UML From Code next, then click Try This Change to load a fresh prompt. If you skip reset later, make a different UML-backed change instead.".replace("\\", "")))
        assertTrue(source.contains("The guided demo prompt likely matches code that is already in \\${'$'}{state.resetPath}. Reset Demo Sandbox is optional but recommended because that file already contains the guided change. If you skip reset, make a different UML-backed change instead.".replace("\\", "")))
    }
}
