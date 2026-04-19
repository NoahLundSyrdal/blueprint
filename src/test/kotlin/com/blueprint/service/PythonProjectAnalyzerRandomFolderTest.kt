package com.blueprint.service

import com.intellij.openapi.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.file.Path

class PythonProjectAnalyzerRandomFolderTest {
    @Test
    fun `detects flat module projects without package init files`() {
        val fixture = Path.of("src/test/resources/random_python_flat_tool").toAbsolutePath().normalize()
        val context = PythonProjectAnalyzer(fakeProject(fixture)).analyze()

        assertEquals(listOf("pyproject.toml"), context.configFiles)
        assertTrue(context.isPythonLikely())
        assertFalse(context.sourceRoots.isEmpty())
        assertFalse(context.sourceRoots.any { it.contains("venv") })
        assertTrue(context.testRoots.contains("tests"))
        assertEquals("pyproject", context.packageManager)
        assertTrue(context.frameworks.contains("pytest"))
        assertTrue(context.frameworks.contains("attrs"))
        assertEquals(listOf("python -m pytest"), context.testCommands)
    }

    @Test
    fun `detects namespace app layouts and pytest fallback guidance`() {
        val fixture = Path.of("src/test/resources/random_python_namespace_app").toAbsolutePath().normalize()
        val context = PythonProjectAnalyzer(fakeProject(fixture)).analyze()

        assertEquals(listOf("pyproject.toml"), context.configFiles)
        assertTrue(context.sourceRoots.contains("app"))
        assertTrue(context.sourceRoots.contains("app/api"))
        assertTrue(context.sourceRoots.contains("app/inventory"))
        assertTrue(context.testRoots.contains("tests"))
        assertEquals("pyproject", context.packageManager)
        assertTrue(context.frameworks.contains("fastapi"))
        assertTrue(context.frameworks.contains("pydantic"))
        assertEquals(listOf("python -m pytest"), context.testCommands)
    }

    private fun fakeProject(basePath: Path): Project =
        Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Project::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getBasePath" -> basePath.toString()
                "getName" -> basePath.fileName.toString()
                "isDisposed" -> false
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        } as Project
}
