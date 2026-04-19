package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class WorkspaceJsonCopyRegressionTest {
    private val workspaceJsonPath = Paths.get("examples/invite_project/.idea/blueprint/workspace.json")

    /**
     * Verifies the checked-in demo workspace does not reintroduce legacy default-flow copy.
     */
    @Test
    fun `demo workspace avoids create code nodes copy`() {
        val source = Files.readString(workspaceJsonPath)

        assertFalse(source.contains("Create Code Nodes"))
    }
}
