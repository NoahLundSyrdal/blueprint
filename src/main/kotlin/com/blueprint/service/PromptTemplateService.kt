package com.blueprint.service

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Loads prompt templates and renders them with {{PLACEHOLDER}} substitution.
 *
 * Resolution order:
 *   1. System property / env `BLUEPRINT_PROMPTS_DIR`
 *   2. classpath resource /prompts/<name>.md
 *   3. <project>/prompts/<name>.md  (walked from CWD, for local overrides)
 *
 * Only the fenced ```text ...``` block from the md file is returned so the
 * "Purpose:" preamble doesn't leak into the LLM call.
 */
@Service(Service.Level.APP)
class PromptTemplateService {

    private val log = Logger.getInstance(PromptTemplateService::class.java)
    private val cache = mutableMapOf<String, String>()

    fun getPrompt(name: String): String {
        cache[name]?.let { return it }
        val raw = loadRaw(name) ?: error("Prompt '$name' not found")
        val body = extractPromptBody(raw)
        cache[name] = body
        return body
    }

    fun renderPrompt(template: String, context: Map<String, String>): String {
        var out = template
        for ((k, v) in context) {
            out = out.replace("{{$k}}", v)
        }
        // Replace any remaining {{PLACEHOLDERS}} with an empty string so the
        // model never sees literal mustache tokens.
        out = Regex("\\{\\{[A-Z0-9_]+}}").replace(out, "")
        return out
    }

    fun renderFromName(name: String, context: Map<String, String>): String =
        renderPrompt(getPrompt(name), context)

    // --- internals ---

    private fun loadRaw(name: String): String? {
        val filenames = listOf("$name.md", name)
        val explicitOverrides = buildList<Path> {
            System.getProperty("BLUEPRINT_PROMPTS_DIR")?.let { dir ->
                filenames.forEach { add(Paths.get(dir, it)) }
            }
            System.getenv("BLUEPRINT_PROMPTS_DIR")?.let { dir ->
                filenames.forEach { add(Paths.get(dir, it)) }
            }
        }

        for (c in explicitOverrides) {
            try {
                if (Files.isRegularFile(c)) {
                    log.info("Loading prompt $name from explicit override $c")
                    return Files.readString(c)
                }
            } catch (_: Exception) { /* continue */ }
        }

        val localOverrides = buildList<Path> {
            val cwd = Paths.get("").toAbsolutePath()
            var p: Path? = cwd
            repeat(6) {
                p?.let {
                    filenames.forEach { filename ->
                        add(it.resolve("prompts").resolve(filename))
                    }
                }
                p = p?.parent
            }
        }

        for (filename in listOf("$name.md", name)) {
            javaClass.getResourceAsStream("/prompts/$filename")?.use {
                log.info("Loading prompt $name from classpath")
                return it.readBytes().toString(Charsets.UTF_8)
            }
        }

        for (c in localOverrides) {
            try {
                if (Files.isRegularFile(c)) {
                    log.info("Loading prompt $name from local override $c")
                    return Files.readString(c)
                }
            } catch (_: Exception) { /* continue */ }
        }

        log.warn("Prompt $name not found in: ${(explicitOverrides + localOverrides).joinToString()}")
        log.warn("IntelliJ config dir: ${PathManager.getConfigPath()}")
        return null
    }

    /**
     * Prompt files wrap the actual template inside a fenced ```text ... ```
     * block. Extract that; fall back to full content.
     */
    private fun extractPromptBody(raw: String): String {
        val fence = Regex("(?s)```(?:text)?\\s*\\n(.*?)\\n```")
        return fence.find(raw)?.groupValues?.getOrNull(1)?.trim() ?: raw.trim()
    }
}
