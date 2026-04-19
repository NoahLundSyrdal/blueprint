package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class WhatChangedAfterApplyReceiptTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    @Test
    fun `apply chat summary reuses what changed summary and refreshed uml confirmation`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("appendChat("))
        assertTrue(source.contains("listOf(nextActionLine, summaryLine, whatChanged, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, verifyStateLine, highlightLine)"))
        assertTrue(source.contains("val whatChanged = PatchChangeSummary.applySummary(registry.getExecution(node.id), changedPaths)"))
        assertTrue(source.contains("val undoNote = if (undoLastApplyButton.isEnabled)"))
        assertTrue(source.contains("val umlRefreshLine = \"Blueprint automatically refreshed the code-backed UML from disk after apply.\""))
        assertTrue(source.contains("val highlightLine = postApplyHighlightMessage ?: \"Blueprint refreshed the code-backed UML after apply.\""))
    }
}
