package com.blueprint.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityReceiptFormatterTest {
    @Test
    fun `formats core workflow events like a receipt`() {
        assertEquals(
            "Step 1 complete - Scanned project and generated UML: 3 class(es), 2 relationship(s).",
            receiptTextForTest("Abstracted code to UML: 3 class(es), 2 relationship(s)."),
        )
        assertEquals(
            "Step 2 started - Generate Code Diff for Invite schema with Mode: MOCK.",
            receiptTextForTest("Planning code diff for Invite schema with Mode: MOCK."),
        )
        assertEquals(
            "Step 2 complete - Generate Code Diff found no UML-backed work items ready to run.",
            receiptTextForTest("Generate Code Diff found no UML-backed work items ready to run."),
        )
        assertEquals(
            "Step 2 complete - Generated reviewed code patch for Invite schema: APPROVE. Files: app/models.py",
            receiptTextForTest("Code diff ready for Invite schema: APPROVE. Files: app/models.py"),
        )
        assertEquals(
            "Step 4 complete - Applied approved changes for Invite schema: 1 applied, 0 skipped.",
            receiptTextForTest("Apply finished for Invite schema: 1 applied, 0 skipped."),
        )
        assertEquals(
            "Step 4 validation started - Invite schema: pytest -q",
            receiptTextForTest("Running validation after apply for Invite schema: pytest -q"),
        )
        assertEquals(
            "Step 4 validation skipped - Invite schema: no command inferred.",
            receiptTextForTest("Validation skipped after apply for Invite schema: no command inferred."),
        )
        assertEquals(
            "Step 2 blocked - Generate Code Diff kept the last reviewed patch because the current UML could not be parsed.",
            receiptTextForTest("Generate Code Diff used existing nodes because the UML text was not parseable."),
        )
        assertEquals(
            "Step 2 complete - Generate Code Diff found no file changes because the current UML-backed request already matched the code on disk.",
            receiptTextForTest("Generate Code Diff completed with no-op result across 2 node(s)."),
        )
        assertEquals(
            "Step 2 complete - No file changes were needed for Invite schema because that UML-backed request already matched the code on disk.",
            receiptTextForTest("No-op code diff for Invite schema; generated content matched disk."),
        )
        assertEquals(
            "Step 2 blocked - Generate Code Diff stopped at planning for Invite schema. Review the blocked plan before continuing.",
            receiptTextForTest("Code diff blocked at plan for Invite schema"),
        )
        assertEquals(
            "Blocked by dependencies for Invite schema: waiting on schema patch",
            receiptTextForTest("Cannot execute Invite schema yet: waiting on schema patch"),
        )
        assertEquals(
            "Step 3 complete - Review approved Invite schema because Invite + accepted_at: datetime stays in scope and no blocking safety issues were reported.",
            receiptTextForTest("Review approved Invite schema because Invite + accepted_at: datetime stays in scope and no blocking safety issues were reported."),
        )
        assertEquals(
            "Step 5 complete - Refreshed UML from code after apply: refreshed UML from disk with 4 class(es), 3 relationship(s).",
            receiptTextForTest("Freshness verified after apply: refreshed UML from disk with 4 class(es), 3 relationship(s)."),
        )
        assertEquals(
            "Opened reviewed diff for Invite schema (1 file(s)).",
            receiptTextForTest("Opened diff preview for Invite schema (1 file(s))."),
        )
        assertEquals(
            "Optional inspection - Inspected the only changed file after apply: app/models.py",
            receiptTextForTest("Inspected the only changed file after apply: app/models.py"),
        )
        assertEquals(
            "Optional inspection - Inspected one changed file after apply from the chooser: app/models.py",
            receiptTextForTest("Inspected one changed file after apply from the chooser: app/models.py"),
        )
        assertEquals(
            "Step 6 complete - Run the changed app with python main.py. Confirmed visible result: InviteReminder appears on the screen",
            receiptTextForTest("Demo e2e step passed: Run the changed app with python main.py. Confirmed visible result: InviteReminder appears on the screen"),
        )
        assertEquals(
            "Step 6 blocked - Run the changed app could not start because no run command was inferred.",
            receiptTextForTest("Demo e2e step failed: Run the changed app could not start because no run command was inferred."),
        )
        assertEquals(
            "Opened source file for Invite: app/models.py:12",
            receiptTextForTest("Opened source for Invite: app/models.py:12"),
        )
        assertEquals(
            "Import blocked - Could not build UML from the imported text because no entities were found.",
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
        assertEquals(
            "Rolled back apply for 'Invite schema': 1 restored, 0 skipped.",
            receiptTextForTest("Undo apply for 'Invite schema': 1 restored, 0 skipped."),
        )
    }

    @Test
    fun `formats receipt-only helper and review result branches`() {
        assertEquals(
            "Step complete - Refined the UML draft from chat context and grounded source facts.",
            receiptTextForTest("Updated the UML using Current code map, so the edit stays tied to the selected source facts, fields, methods, and relationships."),
        )
        assertEquals(
            "Step 3 blocked - Review blocked Invite schema because scope drift was detected.",
            receiptTextForTest("Review blocked Invite schema because scope drift was detected."),
        )
        assertEquals(
            "Step 3 review result - APPROVE Invite schema because it stays in scope.",
            receiptTextForTest("Review APPROVE Invite schema because it stays in scope."),
        )
        assertEquals(
            "Step 4 validation passed - pytest -q",
            receiptTextForTest("Validation passed: pytest -q"),
        )
        assertEquals(
            "Step 4 validation failed - pytest -q",
            receiptTextForTest("Validation failed: pytest -q"),
        )
        assertEquals(
            "Optional run check - Opened likely entry file for manual verification: app/main.py",
            receiptTextForTest("Opened likely entry file for manual verification: app/main.py"),
        )
        assertEquals(
            "Optional advanced view - Refreshed dependency wave preview",
            receiptTextForTest("Refreshed dependency wave preview"),
        )
    }

    @Test
    fun `leaves unrelated activity lines unchanged`() {
        val message = "Workspace reset to defaults"
        assertEquals(message, receiptTextForTest(message))
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
