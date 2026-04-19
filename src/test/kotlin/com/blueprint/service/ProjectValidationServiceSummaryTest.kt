package com.blueprint.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectValidationServiceSummaryTest {
    @Test
    fun `summary line and detail label distinguish standard fallback and skipped validation`() {
        val standard = ProjectValidationService.ValidationResult(
            status = ProjectValidationService.ValidationResult.Status.PASS,
            command = "python -m pytest",
        )
        val fallback = ProjectValidationService.ValidationResult(
            status = ProjectValidationService.ValidationResult.Status.FAIL,
            command = "python -m pytest; built-in test fallback",
            mode = ProjectValidationService.ValidationResult.Mode.FALLBACK,
        )
        val skipped = ProjectValidationService.ValidationResult(
            status = ProjectValidationService.ValidationResult.Status.SKIPPED,
            reason = "No Python validation command was inferred for this project.",
        )

        assertEquals("Validation", standard.detailLabel())
        assertEquals("Validation passed: python -m pytest", standard.summaryLine())
        assertEquals("Fallback validation", fallback.detailLabel())
        assertEquals("Fallback validation failed: python -m pytest; built-in test fallback", fallback.summaryLine())
        assertEquals("Validation skipped: No Python validation command was inferred for this project.", skipped.summaryLine())
    }
}
