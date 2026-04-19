package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ActionPanelReachabilityRegressionTest {
    @Test
    fun `primary workflow controls stay above first run demo copy`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))
        val primaryActionIndex = source.indexOf("add(primaryActionButton.apply")
        val firstRunDemoIndex = source.indexOf("border = BorderFactory.createTitledBorder(\"First-Run Demo\")")

        assertTrue(primaryActionIndex >= 0)
        assertTrue(firstRunDemoIndex >= 0)
        assertTrue(primaryActionIndex < firstRunDemoIndex)
    }

    @Test
    fun `lower workflow controls are bounded and scrollable`() {
        val source = Files.readString(Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt"))

        assertTrue(source.contains("val actionsScrollPane = JBScrollPane(actions).apply"))
        assertTrue(source.contains("add(actionsScrollPane, BorderLayout.NORTH)"))
        assertTrue(source.contains("horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER"))
        assertTrue(source.contains("add(JBScrollPane(firstRunScenarioArea).apply"))
        assertTrue(source.contains("add(JBScrollPane(demoReceiptArea).apply"))
    }
}
