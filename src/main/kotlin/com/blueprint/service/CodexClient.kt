package com.blueprint.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Thin, vendor-neutral LLM client. Returns raw text (never assumes JSON).
 *
 * Providers supported:
 *  - "anthropic": /v1/messages, env ANTHROPIC_API_KEY, model via BLUEPRINT_MODEL
 *                (default claude-opus-4-6)
 *  - "openai":    /v1/chat/completions, env OPENAI_API_KEY
 *  - "mock":      returns a canned JSON stub (for UI smoke-tests with no net)
 *
 * Select with BLUEPRINT_LLM_PROVIDER env var or .env, defaults to "openai".
 */
@Service(Service.Level.APP)
class CodexClient {

    private val log = Logger.getInstance(CodexClient::class.java)
    @Volatile private var providerOverride: String? = null
    @Volatile private var openAiKeyOverride: String? = null

    data class Result(val text: String, val ok: Boolean, val error: String? = null, val latencyMs: Long = 0)

    fun setProviderOverride(provider: String?) {
        providerOverride = provider?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "env" }
        log.info("CodexClient provider override set to ${providerOverride ?: "env"}")
    }

    fun providerMode(): String = providerOverride ?: (envValue("BLUEPRINT_LLM_PROVIDER") ?: "openai").lowercase()

    fun setOpenAIKeyOverride(key: String?) {
        openAiKeyOverride = key?.trim()?.takeIf { it.isNotBlank() }
        log.info("CodexClient OpenAI key override ${if (openAiKeyOverride.isNullOrBlank()) "cleared" else "set"}")
    }

    fun hasOpenAIKey(): Boolean =
        !openAiKeyOverride.isNullOrBlank() || !envValue("OPENAI_API_KEY").isNullOrBlank()

    fun openAIKeySource(): String {
        if (!openAiKeyOverride.isNullOrBlank()) return "session key"
        return envEntry("OPENAI_API_KEY")?.source ?: "missing key"
    }

    fun sendPrompt(prompt: String): String {
        val result = sendPromptResult(prompt)
        if (!result.ok) error(result.error ?: "LLM call failed")
        return result.text
    }

    fun sendPromptResult(prompt: String): Result {
        val provider = providerMode()
        log.info("CodexClient.sendPrompt provider=$provider promptLen=${prompt.length}")
        val start = System.currentTimeMillis()
        return try {
            val text = when (provider) {
                "mock" -> mockResponse(prompt)
                "openai" -> callOpenAI(prompt)
                "anthropic" -> callAnthropic(prompt)
                else -> error("Unsupported BLUEPRINT_LLM_PROVIDER '$provider'. Use openai, anthropic, or mock.")
            }
            val ms = System.currentTimeMillis() - start
            log.info("CodexClient response len=${text.length} in ${ms}ms")
            Result(text, ok = true, latencyMs = ms)
        } catch (t: Throwable) {
            log.warn("CodexClient failed", t)
            Result("", ok = false, error = t.message ?: t.javaClass.simpleName,
                   latencyMs = System.currentTimeMillis() - start)
        }
    }

    // --- providers ---

    private fun callAnthropic(prompt: String): String {
        val key = envValue("ANTHROPIC_API_KEY") ?: error("ANTHROPIC_API_KEY not set")
        val model = envValue("BLUEPRINT_MODEL") ?: "claude-opus-4-6"
        val body = """
            {
              "model": ${jsonStr(model)},
              "max_tokens": 8192,
              "messages": [
                { "role": "user", "content": ${jsonStr(prompt)} }
              ]
            }
        """.trimIndent()
        val raw = httpPost(
            url = "https://api.anthropic.com/v1/messages",
            body = body,
            headers = mapOf(
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01",
                "content-type" to "application/json"
            )
        )
        // Very small extractor: pull text blocks out of "content":[{"type":"text","text":"..."}]
        return extractAnthropicText(raw).ifBlank { raw }
    }

    private fun callOpenAI(prompt: String): String {
        val key = openAiKeyOverride ?: envValue("OPENAI_API_KEY") ?: error("OPENAI_API_KEY not set")
        val model = preferredOpenAIModel()
        val body = """
            {
              "model": ${jsonStr(model)},
              "messages": [
                { "role": "user", "content": ${jsonStr(prompt)} }
              ]
            }
        """.trimIndent()
        val raw = httpPost(
            url = "https://api.openai.com/v1/chat/completions",
            body = body,
            headers = mapOf(
                "authorization" to "Bearer $key",
                "content-type" to "application/json"
            )
        )
        return extractOpenAIText(raw).ifBlank { raw }
    }

    private fun mockResponse(prompt: String): String {
        val paths = mockPathsFromPrompt(prompt)
        val title = mockNodeTitle(prompt)
        return when {
            prompt.contains("reviewStatus") -> """
            {
              "reviewStatus": "APPROVE",
              "summary": "Approved for demo apply. The mock patches are deterministic, scoped, and safe to preview before writing.",
              "scopeCompliance": {"result": "PASS", "notes": ["All mock patches target files from FILE_SCOPE.", "No broad refactor or out-of-scope change was proposed."]},
              "acceptanceReview": [
                {
                  "criterion": "Demo node can be planned, executed, reviewed, and applied",
                  "result": "PASS",
                  "evidence": ["Structured plan, execution patches, and review artifacts were produced."],
                  "issues": []
                }
              ],
              "issues": [],
              "positiveSignals": [],
              "recommendedNextAction": "apply",
              "followUpChecks": ["Preview the diff before applying.", "Switch to a live provider before using generated code in production."]
            }
            """.trimIndent()
            prompt.contains("plan", ignoreCase = true) && prompt.contains("filesToTouch") -> """
            {
              "status": "READY",
              "nodeIntent": "Mock plan for ${jsonStr(title).trim('"')}. This deterministic offline response demonstrates Blueprint's scoped plan-before-code workflow.",
              "filesToTouch": [${paths.joinToString(",") { """{"path":${jsonStr(it)},"why":"demo-scoped change"}""" }}],
              "implementationSteps": [
                {"id":"S1","title":"Create scoped implementation","details":"Write the smallest demo implementation inside the selected node file scope.","dependsOn":[]},
                {"id":"S2","title":"Validate acceptance criteria","details":"Check the generated patch against the node criteria and scope.","dependsOn":["S1"]}
              ],
              "contractsToRespect": ["Only touch files inside FILE_SCOPE."],
              "assumptions": ["mock provider enabled"],
              "risks": ["Mock mode is deterministic and intentionally writes simple demo content."],
              "validationPlan": {
                "criteriaMapping": [
                  {"criterion":"Demo flow works offline","howToVerify":"Generate plan, execute node, review, preview diff, and apply."}
                ],
                "testsToAddOrRun": [],
                "manualChecks": ["Open the diff preview and confirm the changed files are scoped."]
              },
              "blockingIssues": []
            }
            """.trimIndent()
            else -> """
            {
              "status": "SUCCESS",
              "summary": "Mock execution generated ${paths.size} deterministic scoped file change(s) for offline demo.",
              "assumptions": ["mock provider enabled"],
              "touchedFiles": [${paths.joinToString(",") { """{"path":${jsonStr(it)},"action":"create","reason":"offline demo patch"}""" }}],
              "plan": ["Create deterministic scoped demo content", "Return full-file patch content for review"],
              "patches": [${paths.joinToString(",") { """{"path":${jsonStr(it)},"action":"create","content":${jsonStr(mockContentFor(it, title))}}""" }}],
              "acceptanceCheck": [
                {"criterion":"Demo flow works offline","result":"PASS","notes":"Mock provider returned deterministic plan, execution, and review JSON."}
              ],
              "validation": {"testsAddedOrUpdated": [], "suggestedCommands": ["Review the diff in Blueprint", "Apply the patch only after review approval"], "risks": ["Mock content is demo scaffolding, not production implementation."]},
              "followUps": ["Switch off mock mode before using Blueprint for real implementation work."]
            }
            """.trimIndent()
        }
    }

    private fun mockPathsFromPrompt(prompt: String): List<String> {
        val pathsBlock = Regex(""""paths"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(prompt)?.groupValues?.getOrNull(1).orEmpty()
        val paths = Regex(""""((?:\\.|[^"\\])*)"""")
            .findAll(pathsBlock)
            .map { unescapeJsonString(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .take(4)
            .toList()
        if (paths.isNotEmpty()) return paths

        val globsBlock = Regex(""""globs"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(prompt)?.groupValues?.getOrNull(1).orEmpty()
        val globs = Regex(""""((?:\\.|[^"\\])*)"""")
            .findAll(globsBlock)
            .map { unescapeJsonString(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .take(2)
            .map {
                it.substringBefore("**").substringBefore("*").trimEnd('/').ifBlank { "src" } +
                    "/blueprint_mock_demo.py"
            }
            .toList()
        if (globs.isNotEmpty()) return globs

        val dirsBlock = Regex(""""directories"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(prompt)?.groupValues?.getOrNull(1).orEmpty()
        val dirs = Regex(""""((?:\\.|[^"\\])*)"""")
            .findAll(dirsBlock)
            .map { unescapeJsonString(it.groupValues[1]).trimEnd('/') }
            .filter { it.isNotBlank() }
            .take(2)
            .map { "$it/blueprint_mock_demo.py" }
            .toList()
        return dirs.ifEmpty { listOf("src/example.py") }
    }

    private fun mockNodeTitle(prompt: String): String {
        val title = Regex(""""title"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .find(prompt)?.groupValues?.getOrNull(1)
            ?.let(::unescapeJsonString)
            ?.takeIf { it.isNotBlank() }
        return title ?: "Blueprint demo node"
    }

    private fun mockContentFor(path: String, title: String): String {
        val safeTitle = title.replace('\n', ' ').trim().ifBlank { "Blueprint demo" }
        return when (path.substringAfterLast('.', "").lowercase()) {
            "kt" -> """
                package blueprint.demo

                object BlueprintDemoArtifact {
                    const val title: String = "$safeTitle"
                    const val generatedBy: String = "Blueprint mock provider"
                    const val note: String = "Deterministic offline demo output"
                }
            """.trimIndent() + "\n"
            "ts", "tsx", "js", "jsx" -> """
                // Blueprint deterministic mock output.
                // Safe demo scaffold for: ${jsonStr(safeTitle)}
                export const blueprintDemoArtifact = {
                  title: ${jsonStr(safeTitle)},
                  generatedBy: "Blueprint mock provider",
                  note: "Deterministic offline demo output"
                };
            """.trimIndent() + "\n"
            "md" -> """
                # $safeTitle

                Generated by the Blueprint mock provider for an offline demo.

                - Scope: ${path}
                - Review: deterministic mock approval required before apply
                - Safety: this file is intended for demo use
            """.trimIndent() + "\n"
            "json" -> """
                {
                  "title": ${jsonStr(safeTitle)},
                  "generatedBy": "Blueprint mock provider"
                }
            """.trimIndent() + "\n"
            "py" -> """
                ${"\"\"\"Blueprint deterministic mock output.\"\"\""}

                BLUEPRINT_DEMO_ARTIFACT = {
                    "title": ${jsonStr(safeTitle)},
                    "generated_by": "Blueprint mock provider",
                    "note": "Deterministic offline demo output",
                }
            """.trimIndent() + "\n"
            else -> "Blueprint mock provider output for $safeTitle\n"
        }
    }

    // --- http + parsing helpers ---

    private fun httpPost(url: String, body: String, headers: Map<String, String>): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 180_000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
        if (status !in 200..299) error("HTTP $status: $text")
        return text
    }

    private data class EnvEntry(val value: String, val source: String)

    private fun preferredOpenAIModel(): String =
        envValue("BLUEPRINT_MODEL")
            ?: envValue("OPENAI_MODEL")
            ?: "gpt-5"

    private fun envValue(name: String): String? = envEntry(name)?.value

    private fun envEntry(name: String): EnvEntry? {
        System.getenv(name)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return EnvEntry(it, name)
        }
        return dotenvFiles().firstNotNullOfOrNull { readDotenv(it, name) }
    }

    private fun dotenvFiles(): List<Path> {
        val files = mutableListOf<Path>()
        System.getProperty("BLUEPRINT_ENV_FILE")?.trim()?.takeIf { it.isNotBlank() }?.let {
            files.add(Paths.get(it))
        }
        System.getenv("BLUEPRINT_ENV_FILE")?.trim()?.takeIf { it.isNotBlank() }?.let {
            files.add(Paths.get(it))
        }
        var dir: Path? = Paths.get("").toAbsolutePath()
        repeat(8) {
            val current = dir ?: return@repeat
            files.add(current.resolve(".env"))
            dir = current.parent
        }
        return files.distinct()
    }

    private fun readDotenv(path: Path, name: String): EnvEntry? {
        if (!Files.isRegularFile(path)) return null
        return runCatching {
            Files.readAllLines(path, StandardCharsets.UTF_8).firstNotNullOfOrNull { raw ->
                val line = raw.trim()
                if (line.isBlank() || line.startsWith("#")) return@firstNotNullOfOrNull null

                val normalized = line.removePrefix("export ").trim()
                val equals = normalized.indexOf('=')
                if (equals > 0) {
                    val key = normalized.substring(0, equals).trim()
                    if (key != name) return@firstNotNullOfOrNull null
                    stripEnvValue(normalized.substring(equals + 1))
                        ?.let { EnvEntry(it, "$name from ${path.fileName}") }
                } else if (name == "OPENAI_API_KEY" && normalized.startsWith("sk-")) {
                    EnvEntry(stripEnvValue(normalized) ?: normalized, "raw OpenAI key from ${path.fileName}")
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun stripEnvValue(raw: String): String? {
        val trimmed = Regex("""\s+#.*$""").replace(raw.trim(), "")
        if (trimmed.isBlank()) return null
        if (trimmed.length >= 2) {
            val first = trimmed.first()
            val last = trimmed.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length - 1).trim().takeIf { it.isNotBlank() }
            }
        }
        return trimmed
    }

    private fun jsonStr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun extractAnthropicText(raw: String): String {
        // crude: find "text":"..." after "type":"text"
        val rx = Regex("\"type\"\\s*:\\s*\"text\"\\s*,\\s*\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        return rx.findAll(raw).joinToString("\n") { unescapeJsonString(it.groupValues[1]) }
    }

    private fun extractOpenAIText(raw: String): String {
        val rx = Regex("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        return rx.find(raw)?.groupValues?.get(1)?.let(::unescapeJsonString) ?: ""
    }

    private fun unescapeJsonString(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    '"', '\\', '/' -> { sb.append(n); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    'u' -> {
                        if (i + 5 < s.length) {
                            sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6
                        } else { sb.append(c); i++ }
                    }
                    else -> { sb.append(c); i++ }
                }
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }
}
