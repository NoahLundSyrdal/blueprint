package com.blueprint.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectValidationServiceTest {
    @Test
    fun `prefers inferred pytest command`() {
        val context = context(
            testCommands = listOf("python -m unittest discover tests", "python -m pytest"),
            testRoots = listOf("tests"),
        )

        assertEquals("python -m pytest", ProjectValidationService.chooseValidationCommand(context))
    }

    @Test
    fun `falls back to pytest when tests exist but analyzer has no command`() {
        val context = context(
            testCommands = emptyList(),
            testRoots = listOf("tests"),
        )

        assertEquals("python -m pytest", ProjectValidationService.chooseValidationCommand(context))
    }

    @Test
    fun `returns no command for projects without tests`() {
        val context = context(
            testCommands = emptyList(),
            testRoots = emptyList(),
            configFiles = emptyList(),
        )

        assertNull(ProjectValidationService.chooseValidationCommand(context))
    }

    @Test
    fun `summarizes failure output to actionable lines`() {
        val output = """
            collected 2 items

            tests/test_models.py F

            =================================== FAILURES ===================================
            ______________________ test_inventory_vehicle_policy ______________________
            tests/test_models.py:17: in test_inventory_vehicle_policy
                assert vehicle.warranty_policy is not None
            E   NameError: name 'date' is not defined
            =========================== short test summary info ============================
            FAILED tests/test_models.py::test_inventory_vehicle_policy - NameError
        """.trimIndent()

        val excerpt = ProjectValidationService.summarizeOutput(output)

        assertTrue(excerpt.contains("tests/test_models.py"))
        assertTrue(excerpt.contains("NameError"))
        assertTrue(excerpt.contains("FAILED"))
    }

    @Test
    fun `summarizes passing output`() {
        val excerpt = ProjectValidationService.summarizeOutput(".. [100%]\n2 passed in 0.03s\n")

        assertTrue(excerpt.contains("passed"))
    }

    @Test
    fun `detects missing pytest output`() {
        assertTrue(ProjectValidationService.isMissingPytest("/opt/python: No module named pytest"))
    }

    private fun context(
        testCommands: List<String>,
        testRoots: List<String>,
        configFiles: List<String> = listOf("pyproject.toml"),
    ): PythonProjectAnalyzer.PythonProjectContext =
        PythonProjectAnalyzer.PythonProjectContext(
            basePath = "/tmp/project",
            configFiles = configFiles,
            sourceRoots = listOf("app"),
            testRoots = testRoots,
            packageManager = "pyproject",
            frameworks = listOf("pytest"),
            testCommands = testCommands,
            notes = emptyList(),
        )
}
