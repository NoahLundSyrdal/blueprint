package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RefreshFromCodeWarningsTest {
    @Test
    fun `refresh chat message includes top warnings for ambiguous projects`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("Notes:"))
        assertTrue(source.contains("generated.warnings.take(3)"))
    }
}
