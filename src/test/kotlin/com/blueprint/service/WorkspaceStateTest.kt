package com.blueprint.service

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceStateTest {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Test
    fun `round trips a populated workspace state`() {
        val original = WorkspaceState(
            savedAt = 1_700_000_000_000L,
            umlText = "classDiagram\nclass Foo",
            umlHasPendingEdits = true,
            selectedCanvasId = "Foo",
            selectedNodeId = "node-1",
            chatTranscript = listOf(
                WorkspaceChatEntry(author = "You", message = "add Bar"),
                WorkspaceChatEntry(author = "Blueprint", message = "Updated UML."),
            ),
            codeMapGroupMode = "PACKAGE",
            hideTests = true,
            hideGenerated = false,
            hideExternalEdges = false,
            hideLowConfidenceEdges = true,
            nodeFilter = "READY",
            advancedMode = true,
            modeBanner = "Restored \u00B7 Viewing: UML draft \u2014 pending edits",
        )

        val json = WorkspaceState.toJson(gson, original)
        val restored = WorkspaceState.fromJson(gson, json)

        assertEquals(original, restored)
    }

    @Test
    fun `restores defaults when fields are missing from older json`() {
        val partial = """
            {
              "version": 1,
              "savedAt": 1700000000000,
              "umlText": "classDiagram"
            }
        """.trimIndent()

        val restored = WorkspaceState.fromJson(gson, partial)
        assertNotNull(restored)
        restored!!
        assertEquals("classDiagram", restored.umlText)
        // Field defaults should fill in everything else, including a non-null transcript list.
        assertEquals(emptyList<WorkspaceChatEntry>(), restored.chatTranscript)
        assertEquals(false, restored.umlHasPendingEdits)
        assertNull(restored.selectedCanvasId)
        assertNull(restored.selectedNodeId)
        assertNull(restored.codeMapGroupMode)
        assertEquals(false, restored.hideTests)
        assertEquals(false, restored.hideGenerated)
        // hideExternalEdges defaults to true because the BlueprintPanel default is "Hide imports" enabled.
        assertEquals(true, restored.hideExternalEdges)
        assertEquals(false, restored.hideLowConfidenceEdges)
        assertNull(restored.nodeFilter)
        assertEquals(false, restored.advancedMode)
        assertNull(restored.modeBanner)
    }

    @Test
    fun `returns null when json is malformed`() {
        val restored = WorkspaceState.fromJson(gson, "{not valid json")
        assertNull(restored)
    }

    @Test
    fun `default constructed state has expected starter values`() {
        val fresh = WorkspaceState()
        assertEquals(WorkspaceState.VERSION, fresh.version)
        assertEquals(0L, fresh.savedAt)
        assertNull(fresh.umlText)
        assertEquals(false, fresh.umlHasPendingEdits)
        assertEquals(emptyList<WorkspaceChatEntry>(), fresh.chatTranscript)
        // Mirrors BlueprintPanel: only "Hide imports" starts on.
        assertEquals(true, fresh.hideExternalEdges)
        assertEquals(false, fresh.hideTests)
        assertEquals(false, fresh.hideGenerated)
        assertEquals(false, fresh.hideLowConfidenceEdges)
        assertTrue(fresh.chatTranscript.isEmpty())
    }
}
