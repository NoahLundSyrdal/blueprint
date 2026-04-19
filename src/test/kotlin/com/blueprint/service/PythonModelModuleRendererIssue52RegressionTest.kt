package com.blueprint.service

import com.blueprint.model.NodeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonModelModuleRendererIssue52RegressionTest {
    @Test
    fun `merge preserves top level code that follows an existing class`() {
        val existing = """
            from dataclasses import dataclass

            @dataclass
            class Invite:
                id: str

            def build_slug(value: str) -> str:
                return value.lower()
        """.trimIndent()

        val result = PythonModelModuleRenderer.renderWithReport(
            listOf(
                NodeContract(
                    name = "Invite",
                    kind = "schema",
                    schema = """
                        id: str
                        email: str
                    """.trimIndent(),
                ),
            ),
            existingContent = existing,
        )

        assertTrue(result.content.contains("class Invite:\n    id: str\n    email: str"))
        assertTrue(result.content.contains("def build_slug(value: str) -> str:\n    return value.lower()"))
        assertEquals(listOf("add field Invite.email"), result.changes)
    }

    @Test
    fun `merge inserts a new related dataclass next to its owning model instead of at module end`() {
        val existing = """
            from dataclasses import dataclass

            @dataclass
            class Invite:
                id: str
                project: Project

            def helper() -> str:
                return "ok"
        """.trimIndent()

        val result = PythonModelModuleRenderer.renderWithReport(
            listOf(
                NodeContract(
                    name = "Project",
                    kind = "schema",
                    schema = """
                        id: str
                        name: str
                    """.trimIndent(),
                ),
                NodeContract(
                    name = "Invite",
                    kind = "schema",
                    schema = """
                        id: str
                        project: Project
                    """.trimIndent(),
                ),
            ),
            existingContent = existing,
        )

        val projectIndex = result.content.indexOf("class Project:")
        val inviteIndex = result.content.indexOf("class Invite:")
        val helperIndex = result.content.indexOf("def helper()")
        assertTrue(projectIndex >= 0)
        assertTrue(inviteIndex >= 0)
        assertTrue(helperIndex >= 0)
        assertTrue(projectIndex < inviteIndex)
        assertTrue(inviteIndex < helperIndex)
        assertFalse(result.content.substring(helperIndex).contains("class Project:"))
        assertTrue(result.changes.contains("add class Project"))
        assertTrue(result.changes.contains("add field Project.id"))
        assertTrue(result.changes.contains("add field Project.name"))
    }

    @Test
    fun `merge adds typing imports for related collections and optional fields`() {
        val existing = """
            from dataclasses import dataclass

            @dataclass
            class Invite:
                id: str
        """.trimIndent()

        val result = PythonModelModuleRenderer.renderWithReport(
            listOf(
                NodeContract(
                    name = "InviteReminder",
                    kind = "schema",
                    schema = "invite_id: str",
                ),
                NodeContract(
                    name = "Invite",
                    kind = "schema",
                    schema = """
                        id: str
                        reminders: List[InviteReminder]
                        approver: Optional[str]
                    """.trimIndent(),
                ),
            ),
            existingContent = existing,
        )

        assertTrue(result.content.contains("from typing import List, Optional\n"))
        assertTrue(result.content.contains("reminders: List[InviteReminder]"))
        assertTrue(result.content.contains("approver: Optional[str]"))
        assertTrue(result.changes.contains("add import from typing import List, Optional"))
        assertTrue(result.changes.contains("add field Invite.reminders"))
        assertTrue(result.changes.contains("add field Invite.approver"))
    }
}
