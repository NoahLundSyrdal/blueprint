package com.blueprint.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PythonProjectAnalyzerDetectionTest {
    @Test
    fun `analyzer source-root guidance supports common zero-config layouts`() {
        val prompt = PythonProjectAnalyzer.PythonProjectContext(
            basePath = "/tmp/project",
            configFiles = listOf("pyproject.toml"),
            sourceRoots = listOf("app", "src", "src/domain"),
            testRoots = listOf("tests"),
            packageManager = "pyproject",
            frameworks = listOf("pytest"),
            testCommands = listOf("python -m pytest"),
            runCommands = listOf("python app.py"),
            notes = emptyList(),
        ).promptContext()

        assertTrue(prompt.contains("sourceRoots: app, src, src/domain"))
        assertTrue(prompt.contains("suggestedRunCommands: python app.py"))
        assertTrue(prompt.contains("Prefer Python modules under the detected source roots."))
    }

    @Test
    fun `implementation detects namespace-friendly src and app roots`() {
        val source = Files.readString(java.nio.file.Paths.get("src/main/kotlin/com/blueprint/service/PythonProjectAnalyzer.kt"))

        assertTrue(source.contains("if (baseHasTopLevelPythonFiles(base)) roots += \".\""))
        assertTrue(source.contains("listOf(\"src\", \"app\")"))
        assertTrue(source.contains("return namespaceSourceRoot(base, file)"))
        assertTrue(source.contains("private fun baseHasTopLevelPythonFiles(base: Path): Boolean"))
        assertTrue(source.contains("private fun containsPythonSources(dir: Path, maxDepth: Int): Boolean"))
        assertTrue(source.contains("\"generated\""))
        assertTrue(source.contains("\"vendor\""))
        assertTrue(source.contains("sourceRoots.size > 6"))
    }
}
