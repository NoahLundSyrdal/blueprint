package com.blueprint.ir

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Persists ArchitectureIR to disk as .idea/blueprint/ir.json.
 *
 * JSON (not YAML) because gson is already a dependency and the existing
 * state.json lives in the same directory. Human-editable is still fine:
 * the schema is flat and comment-free.
 */
@Service(Service.Level.PROJECT)
class IRStore(private val project: Project) {

    private val log = Logger.getInstance(IRStore::class.java)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun path(): Path? {
        val base = project.basePath ?: return null
        return Paths.get(base, ".idea", "blueprint", "ir.json")
    }

    fun exists(): Boolean = path()?.let { Files.isRegularFile(it) } == true

    fun save(ir: ArchitectureIR): Path? {
        val target = path() ?: return null
        return try {
            Files.createDirectories(target.parent)
            Files.writeString(target, gson.toJson(ir), StandardCharsets.UTF_8)
            target
        } catch (t: Throwable) {
            log.warn("Failed to save IR", t)
            null
        }
    }

    fun load(): ArchitectureIR? {
        val source = path() ?: return null
        if (!Files.isRegularFile(source)) return null
        return try {
            val text = Files.readString(source, StandardCharsets.UTF_8)
            if (text.isBlank()) null else gson.fromJson(text, ArchitectureIR::class.java)
        } catch (t: Throwable) {
            log.warn("Failed to load IR", t)
            null
        }
    }

    fun serialize(ir: ArchitectureIR): String = gson.toJson(ir)
}
