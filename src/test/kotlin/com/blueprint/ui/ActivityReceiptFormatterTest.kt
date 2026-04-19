package com.blueprint.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityReceiptFormatterTest {
    @Test
    fun `formats core workflow events like a receipt`() {
        assertEquals(
            "Scanned project and generated UML: 3 class(es), 2 relationship(s).",
            receiptTextForTest("Abstracted code to UML: 3 class(es), 2 relationship(s)."),
        )
        assertEquals(
            "Started Generate Code Diff for Invite schema with Mode: MOCK.",
            receiptTextForTest("Planning code diff for Invite schema with Mode: MOCK."),
        )
        assertEquals(
            "Generate Code Diff found no UML-backed work items ready to run.",
            receiptTextForTest("Generate Code Diff found no UML-backed work items ready to run."),
        )
        assertEquals(
            "Generated reviewed code patch for Invite schema: APPROVE. Files: app/models.py",
            receiptTextForTest("Code diff ready for Invite schema: APPROVE. Files: app/models.py"),
        )
        assertEquals(
            "Applied approved changes for Invite schema: 1 applied, 0 skipped.",
            receiptTextForTest("Apply finished for Invite schema: 1 applied, 0 skipped."),
        )
        assertEquals(
            "Started validation after apply for Invite schema: pytest -q",
            receiptTextForTest("Running validation after apply for Invite schema: pytest -q"),
        )
        assertEquals(
            "Skipped validation after apply for Invite schema: no command inferred.",
            receiptTextForTest("Validation skipped after apply for Invite schema: no command inferred."),
        )
        assertEquals(
            "Review approved Invite schema because Invite + accepted_at: datetime stays in scope and no blocking safety issues were reported.",
            receiptTextForTest("Review approved Invite schema because Invite + accepted_at: datetime stays in scope and no blocking safety issues were reported."),
        )
        assertEquals(
            "Refreshed UML from code after apply: refreshed UML from disk with 4 class(es), 3 relationship(s).",
            receiptTextForTest("Freshness verified after apply: refreshed UML from disk with 4 class(es), 3 relationship(s)."),
        )
        assertEquals(
            "Opened reviewed diff for Invite schema (1 file(s)).",
            receiptTextForTest("Opened diff preview for Invite schema (1 file(s))."),
        )
        assertEquals(
            "Opened source file for Invite: app/models.py:12",
            receiptTextForTest("Opened source for Invite: app/models.py:12"),
        )
        assertEquals(
            "Could not build UML from the imported text because no entities were found.",
            receiptTextForTest("UML import found no entities."),
        )
        assertEquals(
            "Saved workflow node Invite schema",
            receiptTextForTest("Saved node Invite schema"),
        )
        assertEquals(
            "Removed workflow node Invite schema",
            receiptTextForTest("Removed node Invite schema"),
        )
    }

    @Test
    fun `leaves unrelated activity lines unchanged`() {
        val message = "Workspace reset to defaults"
        assertEquals(message, receiptTextForTest(message))
    }

    @Test
    fun `formats uml refinement receipt with clearer wording`() {
        assertEquals(
            "Refined the UML draft from chat context and grounded source facts.",
            receiptTextForTest("Updated the UML using Current code map, so the edit stays tied to the selected source facts, fields, methods, and relationships."),
        )
    }
}

private fun receiptTextForTest(message: String): String {
    val method = BlueprintPanel::class.java.getDeclaredMethod("receiptText", String::class.java)
    method.isAccessible = true
    val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    val instance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        .invoke(unsafe, BlueprintPanel::class.java) as BlueprintPanel
    return method.invoke(instance, message) as String
}
