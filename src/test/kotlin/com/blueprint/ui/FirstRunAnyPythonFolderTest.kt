package com.blueprint.ui

import com.blueprint.service.PythonProjectAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunAnyPythonFolderTest {
    @Test
    fun `checklist explains inferred run and validation for a non-demo python folder`() {
        val context = PythonProjectAnalyzer.PythonProjectContext(
            basePath = "examples/car_company_project",
            configFiles = listOf("pyproject.toml"),
            sourceRoots = listOf("app"),
            testRoots = listOf("tests"),
            packageManager = "pyproject",
            frameworks = listOf("pytest"),
            testCommands = listOf("python -m pytest"),
            runCommands = listOf("python -m app"),
            runEntryCandidates = listOf("app/__main__.py", "app/models.py"),
            notes = emptyList(),
            filesAnalyzed = listOf("app/__init__.py", "app/__main__.py", "app/models.py", "tests/test_models.py"),
        )

        assertEquals(
            """
                Python quick checklist
                - Source roots: app
                - Validation command: python -m pytest
                - Run command: python -m app
                - Run entry candidates: app/__main__.py, app/models.py
                - Scope summary: 4 Python files analyzed
            """.trimIndent(),
            context.summaryChecklist(),
        )

        val checklist = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = false,
            reviewApprovedReady = false,
            appliedReady = false,
            refreshedCodeMapReady = false,
            runCommand = context.runCommands.first(),
            runEntryCandidates = context.runEntryCandidates,
            validationCommand = context.testCommands.first(),
            validationReady = false,
            validationPassed = false,
            runVerified = false,
        ).checklistText()

        assertTrue(checklist.contains("Run readiness: Blueprint inferred python -m app for this project."))
        assertTrue(checklist.contains("Run decision: Blueprint inferred this command because the current Python folder looks runnable and includes likely entry files such as app/__main__.py, app/models.py. Run command: python -m app"))
        assertTrue(checklist.contains("[next] Generate Code Diff -> create a reviewed code patch from your UML edits."))
        assertTrue(checklist.contains("Blueprint will validate after apply with: python -m pytest"))
    }

    @Test
    fun `checklist degrades gracefully for a non-demo folder without run or validation commands`() {
        val context = PythonProjectAnalyzer.PythonProjectContext(
            basePath = "examples/car_company_project",
            configFiles = listOf("pyproject.toml"),
            sourceRoots = listOf("app"),
            testRoots = emptyList(),
            packageManager = "pyproject",
            frameworks = emptyList(),
            testCommands = emptyList(),
            runCommands = emptyList(),
            runEntryCandidates = listOf("app/models.py"),
            notes = listOf("No runnable entry point inferred from the current folder."),
            filesAnalyzed = listOf("app/__init__.py", "app/models.py"),
        )

        assertEquals(
            "2 Python files analyzed",
            context.scopeSummaryLine(),
        )

        val checklist = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = true,
            reviewApprovedReady = true,
            appliedReady = true,
            refreshedCodeMapReady = true,
            runCommand = null,
            runEntryCandidates = context.runEntryCandidates,
            validationCommand = null,
            validationReady = false,
            validationPassed = false,
            runVerified = false,
        ).checklistText()

        assertTrue(checklist.contains("Run readiness: Blueprint has not inferred a project run command yet."))
        assertTrue(checklist.contains("Run decision: Blueprint could not infer a run command yet because none of the likely entry files mapped to a single safe default command."))
        assertTrue(checklist.contains("- Open Likely Entry File to jump into the best candidate."))
        assertTrue(checklist.contains("- Likely entry files: app/models.py"))
        assertTrue(checklist.contains("No validation command was inferred. After Apply Approved Changes, verify manually or run your preferred checks."))
    }
}
