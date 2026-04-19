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
        assertTrue(context.sourceRoots.contains("."))
        assertFalse(context.sourceRoots.isEmpty())
        assertFalse(context.sourceRoots.any { it.contains("venv") })
        assertTrue(context.testRoots.contains("tests"))
        assertEquals("pyproject", context.packageManager)
        assertTrue(context.frameworks.contains("pytest"))
        assertTrue(context.frameworks.contains("attrs"))
        assertEquals(listOf("python -m pytest", "python -m unittest discover tests"), context.testCommands)
        assertEquals(listOf("python main.py"), context.runCommands)
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
        assertEquals(listOf("python -m pytest", "python -m unittest discover tests"), context.testCommands)
        assertEquals(listOf("uvicorn app.api.server:app --reload", "python app/api/server.py"), context.runCommands)
    }

    @Test
    fun `detects setup cfg src package layouts and skips vendor roots`() {
        val fixture = Path.of("src/test/resources/random_python_setup_cfg_pkg").toAbsolutePath().normalize()
        val context = PythonProjectAnalyzer(fakeProject(fixture)).analyze()

        assertEquals(listOf("setup.cfg"), context.configFiles)
        assertTrue(context.sourceRoots.contains("src"))
        assertTrue(context.sourceRoots.contains("src/orders"))
        assertTrue(context.testRoots.contains("tests"))
        assertEquals("unknown", context.packageManager)
        assertTrue(context.frameworks.contains("flask"))
        assertEquals(listOf("python -m pytest", "python -m unittest discover tests"), context.testCommands)
        assertEquals(listOf("flask --app src.orders.api run"), context.runCommands)
        assertFalse(context.sourceRoots.any { it.contains("site-packages") })
        assertFalse(context.sourceRoots.any { it.contains("build") })
        assertFalse(context.sourceRoots.any { it.contains("vendor") })
    }

    @Test
    fun `detects setup py namespace package layouts`() {
        val fixture = Path.of("src/test/resources/random_python_setup_py_namespace").toAbsolutePath().normalize()
        val context = PythonProjectAnalyzer(fakeProject(fixture)).analyze()

        assertEquals(listOf("setup.py"), context.configFiles)
        assertTrue(context.isPythonLikely())
        assertTrue(context.sourceRoots.contains("warehouse"))
        assertTrue(context.sourceRoots.contains("warehouse") || context.sourceRoots.contains("warehouse/domain"))
        assertTrue(context.testRoots.contains("tests"))
        assertEquals("unknown", context.packageManager)
        assertEquals(listOf("python -m unittest discover tests"), context.testCommands)
        assertEquals(listOf("python -m warehouse"), context.runCommands)
    }

    @Test
    fun `run command inference never returns an empty module command`() {
        val fixtures = listOf(
            "src/test/resources/random_python_flat_tool" to "python main.py",
            "src/test/resources/random_python_namespace_app" to "uvicorn app.api.server:app --reload",
            "src/test/resources/random_python_setup_py_namespace" to "python -m warehouse",
        )

        fixtures.forEach { (fixturePath, expectedFirstCommand) ->
            val fixture = Path.of(fixturePath).toAbsolutePath().normalize()
            val context = PythonProjectAnalyzer(fakeProject(fixture)).analyze()

            assertFalse(context.runCommands.any { it == "python -m " || it.endsWith("-m ") })
            assertEquals(expectedFirstCommand, context.runCommands.first())
        }
    }

    @Test
    fun `scope summary reports analyzed and skipped files for mixed folders`() {
        val fixture = Path.of("src/test/resources/random_python_mixed_scope").toAbsolutePath().normalize()
        val context = PythonProjectAnalyzer(fakeProject(fixture)).analyze()

        assertTrue(context.filesAnalyzed.contains("app/models.py"))
        assertTrue(context.filesAnalyzed.contains("app/api/server.py"))
        assertTrue(context.filesAnalyzed.contains("tests/test_server.py"))
        assertTrue(context.skippedFiles.any { it.path == "generated/client_pb2.py" })
        assertTrue(context.skippedFiles.any { it.path == "vendor/third_party.py" })
        assertTrue(context.scopeSummaryLine().contains("3 Python files analyzed, 2 skipped"))
        assertTrue(context.scopeReceipt().contains("Files analyzed: 3"))
        assertTrue(context.scopeReceipt().contains("Scope note: Some Python paths were skipped during Refresh UML From Code"))
        assertTrue(context.scopeReceipt().contains("fix the folder or files if needed, then Refresh UML From Code again before Generate Code Diff"))
        assertTrue(context.scopeReceipt().contains("Blueprint still built the current UML from the Python files it could read."))
        assertTrue(context.scopeReceipt().contains("Inspect the skipped paths below if anything looks incomplete, fix the folder or files if needed, then Refresh UML From Code again before Generate Code Diff."))
        assertTrue(context.scopeReceipt().contains("generated/client_pb2.py"))
        assertTrue(context.promptContext().contains("skippedFiles:"))
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
