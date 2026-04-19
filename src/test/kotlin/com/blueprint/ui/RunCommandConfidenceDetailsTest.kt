package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class RunCommandConfidenceDetailsTest {
    @Test
    fun `checklist names strongest and fallback entry files when no safe run command exists`() {
        val text = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = true,
            reviewApprovedReady = true,
            appliedReady = true,
            refreshedCodeMapReady = true,
            runCommand = null,
            runEntryCandidates = listOf("manage.py", "app.py", "pkg/__main__.py"),
            validationCommand = null,
            validationReady = false,
            validationPassed = false,
            runVerified = false,
        ).checklistText()

        assertTrue(text.contains("Strongest candidate right now: manage.py."))
        assertTrue(text.contains("Other strong candidates: app.py, pkg/__main__.py."))
        assertTrue(text.contains("If that is not the right launcher, try one of these likely entry files: manage.py, app.py, pkg/__main__.py"))
    }

    @Test
    fun `checklist explains why inferred run command won when it maps to strongest candidate`() {
        val text = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = false,
            reviewApprovedReady = false,
            appliedReady = false,
            refreshedCodeMapReady = false,
            runCommand = "python app.py",
            runEntryCandidates = listOf("app.py", "main.py"),
            validationCommand = "python -m pytest",
            validationReady = false,
            validationPassed = false,
            runVerified = false,
        ).checklistText()

        assertTrue(text.contains("Run decision: Blueprint inferred this as the best default run command because python app.py maps directly to the strongest likely entry file app.py. Recommended command: python app.py. Other likely entry file: main.py."))
    }
}
