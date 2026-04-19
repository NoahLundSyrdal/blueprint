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
            "Applied approved changes for Invite schema: 1 applied, 0 skipped.",
            receiptTextForTest("Apply finished for Invite schema: 1 applied, 0 skipped."),
        )
        assertEquals(
            "Refreshed UML from code after apply: refreshed UML from disk with 4 class(es), 3 relationship(s).",
            receiptTextForTest("Freshness verified after apply: refreshed UML from disk with 4 class(es), 3 relationship(s)."),
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
