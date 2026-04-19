package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ChatGroundingSummaryRegressionTest {
    private val panelPath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")
    private val groundingPath = Paths.get("src/main/kotlin/com/blueprint/ui/ChatGroundingContext.kt")

    @Test
    fun `grounding ui surfaces selected context and whole diagram hint`() {
        val panel = Files.readString(panelPath)
        val grounding = Files.readString(groundingPath)

        assertTrue(panel.contains("private val groundingSummaryArea = JBTextArea(4, 40).apply"))
        assertTrue(panel.contains("add(JBScrollPane(groundingSummaryArea))"))
        assertTrue(panel.contains("groundingSummaryArea.text = grounding.summaryText"))
        assertTrue(panel.contains("private fun refreshGroundingSummary()"))
        assertTrue(panel.contains("logActivity(\"Chat refined the UML using \${grounding.activityLabel}.\")"))
        assertTrue(panel.contains("Updated the UML using whole-diagram context. Select a UML card if you want the next edit grounded to one entity's source file, fields, and relationships."))
        assertTrue(panel.contains("Updated the UML using \${grounding.selectedLabel}. Blueprint grounded this edit to the selected source file, fields, methods, and relationships before rewriting the UML."))
        assertTrue(grounding.contains("Hint: select a UML card for a more targeted chat edit."))
        assertTrue(grounding.contains("Grounding: selected code entity"))
        assertTrue(grounding.contains("Grounding: selected UML entity"))
        assertTrue(grounding.contains("Grounding: whole-diagram context"))
    }
}
