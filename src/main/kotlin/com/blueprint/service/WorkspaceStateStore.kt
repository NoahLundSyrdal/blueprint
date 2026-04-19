package com.blueprint.service

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Chat bubble captured from the BlueprintPanel transcript.
 */
data class WorkspaceChatEntry(
    val author: String = "",
    val message: String = "",
)

/**
 * Per-project UI state for the Blueprint tool window. Persisting this lets a
 * user close and reopen the IDE without losing the current UML draft, chat
 * history, code-map filters, or canvas selection.
 *
 * Field defaults match the fresh BlueprintPanel defaults so that loading a
 * partially-populated JSON (e.g. from an older plugin version) still yields a
 * coherent starting state.
 */
data class WorkspaceState(
    val version: Int = VERSION,
    val savedAt: Long = 0L,
    val umlText: String? = null,
    val umlHasPendingEdits: Boolean = false,
    val selectedCanvasId: String? = null,
    val selectedNodeId: String? = null,
    val chatTranscript: List<WorkspaceChatEntry> = emptyList(),
    val codeMapGroupMode: String? = null,
    val hideTests: Boolean = false,
    val hideGenerated: Boolean = false,
    val hideExternalEdges: Boolean = true,
    val hideLowConfidenceEdges: Boolean = false,
    val nodeFilter: String? = null,
    val advancedMode: Boolean = false,
    val modeBanner: String? = null,
) {
    companion object {
        const val VERSION: Int = 1

        fun toJson(gson: Gson, state: WorkspaceState): String = gson.toJson(state)

        fun fromJson(gson: Gson, json: String): WorkspaceState? =
            try {
                gson.fromJson(json, WorkspaceState::class.java)?.normalize()
            } catch (e: JsonSyntaxException) {
                null
            }

        /**
         * Replace fields Gson left as null (because an older JSON omitted them)
         * with the data-class defaults. Without this a partial document would
         * surface `null` collections to the UI even though Kotlin's type
         * system says they can't be null.
         */
        @Suppress("USELESS_ELVIS")
        private fun WorkspaceState.normalize(): WorkspaceState =
            copy(chatTranscript = chatTranscript ?: emptyList())
    }
}

/**
 * Persists WorkspaceState to disk as .idea/blueprint/workspace.json.
 *
 * Follows the same boring convention as NodeRegistry / IRStore: a single
 * pretty-printed JSON file, no IntelliJ PersistentStateComponent.
 */
@Service(Service.Level.PROJECT)
class WorkspaceStateStore(private val project: Project) {

    private val log = Logger.getInstance(WorkspaceStateStore::class.java)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun path(): Path? {
        val base = project.basePath ?: return null
        return Paths.get(base, ".idea", "blueprint", "workspace.json")
    }

    fun exists(): Boolean = path()?.let { Files.isRegularFile(it) } == true

    fun load(): WorkspaceState? {
        val source = path() ?: return null
        if (!Files.isRegularFile(source)) return null
        return try {
            val text = Files.readString(source, StandardCharsets.UTF_8)
            if (text.isBlank()) null else WorkspaceState.fromJson(gson, text)
        } catch (t: Throwable) {
            log.warn("Failed to load workspace state", t)
            null
        }
    }

    fun save(state: WorkspaceState): Path? {
        val target = path() ?: return null
        return try {
            Files.createDirectories(target.parent)
            Files.writeString(target, WorkspaceState.toJson(gson, state), StandardCharsets.UTF_8)
            target
        } catch (t: Throwable) {
            log.warn("Failed to save workspace state", t)
            null
        }
    }

    fun clear(): Boolean {
        val target = path() ?: return false
        return try {
            Files.deleteIfExists(target)
        } catch (t: Throwable) {
            log.warn("Failed to clear workspace state", t)
            false
        }
    }
}
