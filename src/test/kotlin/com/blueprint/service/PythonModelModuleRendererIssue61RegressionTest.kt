package com.blueprint.service

import com.blueprint.model.NodeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonModelModuleRendererIssue61RegressionTest {
    @Test
    fun `merge preserves existing dataclass defaults for unchanged fields`() {
        val existing = """
            from dataclasses import dataclass
            from typing import Optional

            @dataclass
            class Invite:
                status: str = "pending"
                retry_count: int = 0
                approver: Optional[str] = None
        """.trimIndent()

        val result = PythonModelModuleRenderer.renderWithReport(
            listOf(
                NodeContract(
                    name = "Invite",
                    kind = "schema",
                    schema = """
                        status: str
                        retry_count: int
                        approver: Optional[str]
                        email: str
                    """.trimIndent(),
                ),
            ),
            existingContent = existing,
        )

        assertTrue(result.content, result.content.contains("status: str = 'pending'") || result.content.contains("status: str = \"pending\""))
        assertTrue(result.content, result.content.contains("retry_count: int = 0"))
        assertTrue(result.content, result.content.contains("approver: Optional[str] = None"))
        assertTrue(result.content, result.content.contains("email: str"))
        assertFalse(result.changes.contains("update field Invite.status"))
        assertFalse(result.changes.contains("update field Invite.retry_count"))
        assertFalse(result.changes.contains("update field Invite.approver"))
        assertEquals(listOf("add field Invite.email"), result.changes)
    }

    @Test
    fun `merge preserves list default_factory and dataclasses field import when required`() {
        val existing = """
            from dataclasses import dataclass, field
            from typing import List

            @dataclass
            class Invite:
                reminders: List[str] = field(default_factory=list)
        """.trimIndent()

        val result = PythonModelModuleRenderer.renderWithReport(
            listOf(
                NodeContract(
                    name = "Invite",
                    kind = "schema",
                    schema = """
                        reminders: List[str]
                        email: str
                    """.trimIndent(),
                ),
            ),
            existingContent = existing,
        )

        assertTrue(result.content, result.content.contains("from dataclasses import dataclass, field\n"))
        assertTrue(result.content, result.content.contains("reminders: List[str] = field(default_factory=list)"))
        assertTrue(result.content, result.content.contains("email: str"))
        assertEquals(listOf("add field Invite.email"), result.changes)
    }

    @Test
    fun `merge upgrades dataclasses import when preserved default_factory now requires field`() {
        val existing = """
            from dataclasses import dataclass
            from typing import List

            @dataclass
            class Invite:
                reminders: List[str] = field(default_factory=list)
        """.trimIndent()

        val result = PythonModelModuleRenderer.renderWithReport(
            listOf(
                NodeContract(
                    name = "Invite",
                    kind = "schema",
                    schema = "reminders: List[str]".trimIndent(),
                ),
            ),
            existingContent = existing,
        )

        assertTrue(result.content, result.content.contains("from dataclasses import dataclass, field\n"))
        assertTrue(result.content, result.content.contains("reminders: List[str] = field(default_factory=list)"))
        assertTrue(result.changes.toString(), result.changes.contains("add import from dataclasses import dataclass, field"))
    }
}
