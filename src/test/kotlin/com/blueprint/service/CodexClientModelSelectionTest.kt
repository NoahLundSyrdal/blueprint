package com.blueprint.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class CodexClientModelSelectionTest {
    @Test
    fun `preferredOpenAIModel uses launcher model when configured`() {
        val envFile = Files.createTempFile("blueprint-openai-model", ".env")
        try {
            Files.writeString(envFile, "OPENAI_MODEL=gpt-5.4\n")
            System.setProperty("BLUEPRINT_ENV_FILE", envFile.toString())

            assertEquals("gpt-5.4", preferredModel())
        } finally {
            System.clearProperty("BLUEPRINT_ENV_FILE")
            Files.deleteIfExists(envFile)
        }
    }

    @Test
    fun `preferredOpenAIModel prefers BLUEPRINT_MODEL over OPENAI_MODEL`() {
        val envFile = Files.createTempFile("blueprint-model-priority", ".env")
        try {
            Files.writeString(envFile, "OPENAI_MODEL=gpt-5.4\nBLUEPRINT_MODEL=gpt-5.4-pro\n")
            System.setProperty("BLUEPRINT_ENV_FILE", envFile.toString())

            assertEquals("gpt-5.4-pro", preferredModel())
        } finally {
            System.clearProperty("BLUEPRINT_ENV_FILE")
            Files.deleteIfExists(envFile)
        }
    }

    @Test
    fun `preferredOpenAIModel falls back to gpt-5 when no model is configured`() {
        System.clearProperty("BLUEPRINT_ENV_FILE")
        assertEquals("gpt-5", preferredModel())
    }

    private fun preferredModel(): String {
        val method = CodexClient::class.java.getDeclaredMethod("preferredOpenAIModel")
        method.isAccessible = true
        return method.invoke(CodexClient()) as String
    }
}
