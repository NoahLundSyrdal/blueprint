package com.blueprint.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectValidationServiceTest {
    @Test
    fun `prefers analyzer command ordering`() {
        val context = context(
            testCommands = listOf("python -m unittest discover tests", "python -m pytest"),
            testRoots = listOf("tests"),
        )

        assertEquals("python -m unittest discover tests", ProjectValidationService.chooseValidationCommand(context))
    }

    @Test
    fun `falls back to unittest discover when tests exist but analyzer has no command`() {
        val context = context(
            testCommands = emptyList(),
            testRoots = listOf("tests"),
        )

        assertEquals("python -m unittest discover tests", ProjectValidationService.chooseValidationCommand(context))
    }

    @Test
    fun `falls back to tox or nox before generic runners when configured`() {
        assertEquals(
            "tox",
            ProjectValidationService.chooseValidationCommand(
                context(testCommands = emptyList(), testRoots = listOf("tests"), configFiles = listOf("tox.ini")),
            ),
        )
        assertEquals(
            "nox",
            ProjectValidationService.chooseValidationCommand(
                context(testCommands = emptyList(), testRoots = listOf("tests"), configFiles = listOf("noxfile.py")),
            ),
        )
    }

    @Test
    fun `falls back to pytest for setup cfg projects without discovered tests`() {
        val context = context(
            testCommands = emptyList(),
            testRoots = emptyList(),
            configFiles = listOf("setup.cfg"),
        )

        assertEquals("python -m pytest", ProjectValidationService.chooseValidationCommand(context))
    }

    @Test
    fun `falls back to import compile validation for source only projects`() {
        val context = context(
            testCommands = emptyList(),
            testRoots = emptyList(),
            configFiles = emptyList(),
        )

        val command = ProjectValidationService.chooseValidationCommand(context)

        assertTrue(command!!.contains("compileall.compile_dir"))
    }

    @Test
    fun `returns no command for projects without sources or tests`() {
        val context = PythonProjectAnalyzer.PythonProjectContext(
            basePath = "/tmp/project",
            configFiles = emptyList(),
            sourceRoots = emptyList(),
            testRoots = emptyList(),
            packageManager = "unknown",
            frameworks = emptyList(),
            testCommands = emptyList(),
            runCommands = emptyList(),
            runEntryCandidates = emptyList(),
            notes = emptyList(),
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
            runCommands = emptyList(),
            runEntryCandidates = emptyList(),
            notes = emptyList(),
        )
}
