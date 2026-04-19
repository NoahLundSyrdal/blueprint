package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class OpenAppliedFilesRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `post apply ui exposes one click open changed files action`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private val openAppliedFilesButton = JButton(\"Open Changed Files\").apply {"))
        assertTrue(source.contains("toolTipText = \"Optional after verification: open the file(s) Blueprint last wrote to disk to inspect what changed.\""))
        assertTrue(source.contains("addActionListener { openAppliedFiles() }"))
        assertTrue(source.contains("add(openAppliedFilesButton)"))
        assertTrue(source.contains("openAppliedFilesButton.isEnabled = changedPaths.isNotEmpty()"))
        assertTrue(source.contains("openAppliedFilesButton.text = if (changedPaths.size == 1) \"Open Changed File\" else \"Open Changed Files\""))
        assertTrue(source.contains("Open Changed Files is optional after verification if you want to inspect what Blueprint wrote."))
    }

    @Test
    fun `open applied files opens directly for one file and shows chooser for multiple files`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun openAppliedFiles() {"))
        assertTrue(source.contains("if (changedPaths.size == 1) {"))
        assertTrue(source.contains("openChangedFile("))
        assertTrue(source.contains("\"Inspected the only changed file after apply: "))
        assertTrue(source.contains("JOptionPane.showInputDialog("))
        assertTrue(source.contains("\"Blueprint - Open Changed Files\""))
        assertTrue(source.contains("changedPaths.toTypedArray()"))
        assertTrue(source.contains("\"Inspected one changed file after apply from the chooser: \$selectedPath\""))
    }
}
