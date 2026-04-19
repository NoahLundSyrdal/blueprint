package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ManualRunVerificationGuidanceTest {
    @Test
    fun `manual verification guidance explains app style folders`() {
        val text = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = true,
            reviewApprovedReady = true,
            appliedReady = true,
            refreshedCodeMapReady = true,
            runCommand = null,
            runEntryCandidates = listOf("app.py", "src/main.py"),
            validationCommand = null,
            validationReady = false,
            validationPassed = false,
            runVerified = false,
        ).checklistText()

        assertTrue(text.contains("Likely entry files: app.py, src/main.py"))
        assertTrue(text.contains("Project shape hint: this looks most like an app or CLI entry flow, so verify the feature in the running output or UI."))
    }

    @Test
    fun `manual verification guidance explains package style folders`() {
        val text = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = true,
            reviewApprovedReady = true,
            appliedReady = true,
            refreshedCodeMapReady = true,
            runCommand = null,
            runEntryCandidates = listOf("pkg/__main__.py", "pkg/tools.py"),
            validationCommand = null,
            validationReady = false,
            validationPassed = false,
            runVerified = false,
        ).checklistText()

        assertTrue(text.contains("Likely entry files: pkg/__main__.py, pkg/tools.py"))
        assertTrue(text.contains("Project shape hint: this looks most like a package-style app entry, so verify the feature from the package entrypoint output."))
    }

    @Test
    fun `manual verification guidance explains library style folders when no run command is inferred`() {
        val text = FirstRunChecklistState(
            codeMapReady = true,
            reviewedDiffReady = true,
            reviewApprovedReady = true,
            appliedReady = true,
            refreshedCodeMapReady = true,
            runCommand = null,
            runEntryCandidates = listOf("models.py", "domain/entities.py"),
            validationCommand = null,
            validationReady = false,
            validationPassed = false,
            runVerified = false,
        ).checklistText()

        assertTrue(text.contains("Likely entry files: models.py, domain/entities.py"))
        assertTrue(text.contains("Project shape hint: Blueprint found Python files but no obvious app launcher, so verify from the strongest likely entry file first."))
    }
}
