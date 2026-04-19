package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PostApplyRunCommandReceiptRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `post apply summary surfaces the inferred run command near validation`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val runBlock = buildString {"))
        assertTrue(source.contains("appendLine(\"Run after apply:\")"))
        assertTrue(source.contains("appendLine(runNote)"))
        assertTrue(source.contains("appendLine(runBlock)"))
    }

    @Test
    fun `post apply receipt keeps fallback run guidance when no run command is inferred`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val runNote = inferredRunNote(pythonContext)"))
        assertTrue(source.contains("?: missingRunCommandGuidance(context)"))
    }
}
