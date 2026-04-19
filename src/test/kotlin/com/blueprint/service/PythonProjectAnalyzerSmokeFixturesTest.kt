package com.blueprint.service

import com.intellij.openapi.project.Project
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.file.Path

class PythonProjectAnalyzerSmokeFixturesTest {
    @Test
    fun `fastapi smoke fixture infers useful roots validation and run command`() {
        val context = analyzeFixture("src/test/resources/random_python_fastapi_app")

        assertTrue(context.isPythonLikely())
        assertTrue(context.sourceRoots.contains("app"))
        assertTrue(context.sourceRoots.contains("app/api"))
        assertTrue(context.sourceRoots.contains("app/models"))
        assertTrue(context.frameworks.contains("fastapi"))
        assertTrue(context.frameworks.contains("pydantic"))
        assertTrue(context.testCommands.isEmpty())
        assertTrue(ProjectValidationService.chooseValidationCommand(context).orEmpty().contains("python -m pytest"))
        assertTrue(context.runCommands.isNotEmpty())
        assertTrue(context.runCommands.first().contains("uvicorn app.api.server:app --reload"))
        assertFalse(context.runCommands.first().isBlank())
    }

    @Test
    fun `cli smoke fixture infers actionable run command and compile fallback validation`() {
        val context = analyzeFixture("src/test/resources/random_python_cli_app")

        assertTrue(context.isPythonLikely())
        assertTrue(context.sourceRoots.contains("."))
        assertTrue(context.testCommands.isEmpty())
        val validationCommand = ProjectValidationService.chooseValidationCommand(context).orEmpty()
        assertFalse(validationCommand.isBlank())
        assertTrue(context.runCommands.isNotEmpty())
        assertTrue(context.runCommands.any { it.isNotBlank() })
    }

    @Test
    fun `namespace library smoke fixture infers module run command and unittest validation`() {
        val context = analyzeFixture("src/test/resources/random_python_namespace_library")

        assertTrue(context.isPythonLikely())
        assertTrue(context.sourceRoots.contains("warehouse"))
        assertTrue(context.testRoots.contains("tests"))
        assertTrue(context.testCommands.contains("python -m unittest discover tests"))
        assertTrue(context.runCommands.contains("python -m warehouse"))
        assertFalse(context.runCommands.first().endsWith("-m "))
        assertFalse(context.runCommands.first().isBlank())
    }

    private fun analyzeFixture(path: String): PythonProjectAnalyzer.PythonProjectContext {
        val fixture = Path.of(path).toAbsolutePath().normalize()
        return PythonProjectAnalyzer(fakeProject(fixture)).analyze()
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
