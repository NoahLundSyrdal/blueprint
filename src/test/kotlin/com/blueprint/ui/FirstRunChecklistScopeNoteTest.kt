package com.blueprint.ui

import com.blueprint.service.PythonProjectAnalyzer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunChecklistScopeNoteTest {
    @Test
    fun `checklist omits scope note when refresh skipped nothing`() {
        val text = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = false,
            reviewApprovedReady = false,
            appliedReady = false,
            refreshedCodeMapReady = false,
            runCommand = null,
            validationCommand = null,
            validationReady = false,
            validationPassed = false,
            runVerified = false,
            skippedFiles = emptyList(),
        ).checklistText()

        assertFalse(text.contains("Scope note:"))
        assertTrue(text.contains("Run readiness: Blueprint has not inferred a project run command yet."))
    }

    @Test
    fun `checklist summarizes singular skipped-file reason cleanly`() {
        val text = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = false,
            reviewApprovedReady = false,
            appliedReady = false,
            refreshedCodeMapReady = false,
            runCommand = null,
            validationCommand = null,
            validationReady = false,
            validationPassed = false,
            runVerified = false,
            skippedFiles = listOf(
                PythonProjectAnalyzer.SkippedFile("scripts/bootstrap.py", "unsupported or non-importable Python file"),
            ),
        ).checklistText()

        assertTrue(text.contains("Run readiness: Blueprint has not inferred a project run command yet."))
        assertTrue(text.contains("- Scope note: 1 Python path was skipped during Refresh UML From Code (unsupported or non-importable Python file). Inspect Skipped paths like scripts/bootstrap.py if the UML looks incomplete."))
    }
}
