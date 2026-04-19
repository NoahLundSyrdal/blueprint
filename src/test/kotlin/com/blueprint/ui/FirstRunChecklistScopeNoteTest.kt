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
        assertTrue(text.contains("Run readiness: no runnable Python entrypoint inferred yet."))
        assertTrue(text.contains("Blueprint could not infer a run command yet because it did not find a clear runnable entry file."))
        assertTrue(text.contains("Try this fallback:"))
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

        assertTrue(text.contains("Run readiness: no runnable Python entrypoint inferred yet."))
        assertTrue(text.contains("Blueprint could not infer a run command yet because it did not find a clear runnable entry file."))
        assertTrue(text.contains("Try this fallback:"))
        assertTrue(text.contains("- Scope note: 1 Python path was skipped during Refresh UML From Code (unsupported or non-importable Python file). The current UML still reflects the Python files Blueprint could read. Inspect skipped paths like scripts/bootstrap.py, fix the folder or files if needed, then Refresh UML From Code again before Generate Code Diff."))
    }
}
