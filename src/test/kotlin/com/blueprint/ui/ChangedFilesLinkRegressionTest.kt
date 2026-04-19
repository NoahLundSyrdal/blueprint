package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ChangedFilesLinkRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `review tab shows changed files as clickable buttons`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private val changedFilesPanel = JPanel().apply {"))
        assertTrue(source.contains("private fun refreshChangedFilesPanel(exec: ExecutionArtifact?)"))
        assertTrue(source.contains("Changed files (open to inspect, not apply):"))
        assertTrue(source.contains("changedPaths.forEach { path ->"))
        assertTrue(source.contains("changedFilesPanel.add(JButton(path).apply {"))
        assertTrue(source.contains("toolTipText = \"Open this changed file in the IDE. This does not apply the reviewed code patch.\""))
        assertTrue(source.contains("addActionListener { openChangedFile(path) }"))
        assertTrue(source.contains("refreshChangedFilesPanel(exec)"))
    }

    @Test
    fun `changed file opener handles missing files clearly`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("private fun openChangedFile(path: String) {"))
        assertTrue(source.contains("openChangedFileWithReceipt(path, \"Opened changed file from review: \$path\")"))
        assertTrue(source.contains("private fun openChangedFileWithReceipt(path: String, receiptMessage: String) {"))
        assertTrue(source.contains("if (!openProjectFile(sourceFile, path)) return"))
        assertTrue(source.contains("Opening a file here does not apply changes. Generate Code Diff and Apply Approved Changes are still separate steps."))
        assertTrue(source.contains("status(\"Opened changed file: \$path\")"))
        assertTrue(source.contains("logActivity(receiptMessage)"))
    }
}
