package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ActionPanelReachabilityRegressionTest {
    @Test
    fun `primary workflow controls are not buried under first run demo copy`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))
        val primaryActionIndex = source.indexOf("add(primaryActionButton.apply")

        assertTrue(primaryActionIndex >= 0)
        assertFalse(source.contains("BorderFactory.createTitledBorder(\"First-Run Demo\")"))
        assertFalse(source.contains("add(JBScrollPane(firstRunScenarioArea).apply"))
        assertFalse(source.contains("add(JBScrollPane(demoReceiptArea).apply"))
    }

    @Test
    fun `lower workflow controls are bounded and scrollable`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("val actionsScrollPane = JBScrollPane(actions).apply"))
        assertTrue(source.contains("add(actionsScrollPane, BorderLayout.NORTH)"))
        assertTrue(source.contains("horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER"))
    }
}
