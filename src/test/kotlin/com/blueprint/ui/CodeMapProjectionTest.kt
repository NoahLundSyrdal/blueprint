package com.blueprint.ui

import com.blueprint.ir.ArchitectureIR
import com.blueprint.ir.Component
import com.blueprint.ir.ComponentKind
import com.blueprint.ir.Edge
import com.blueprint.ir.EdgeKind
import com.blueprint.ir.EdgeTargetKind
import com.blueprint.ir.Field
import com.blueprint.ir.Operation
import com.blueprint.ir.Ownership
import com.blueprint.ir.ProjectMeta
import com.blueprint.ir.SourceRef
import com.blueprint.ir.TypeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeMapProjectionTest {
    @Test
    fun `projects architecture ir into current-code canvas views`() {
        val ir = ArchitectureIR(
            project = ProjectMeta(name = "invite_project"),
            components = listOf(
                Component(
                    id = "models.project",
                    name = "Project",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("app/models.py")),
                    fields = listOf(Field("id", TypeRef("str")), Field("owner", TypeRef("User"))),
                    operations = listOf(Operation("rename")),
                ),
                Component(
                    id = "models.invite",
                    name = "Invite",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("app/models.py")),
                    sourceRef = SourceRef("app/models.py", 12),
                    fields = listOf(Field("id", TypeRef("str")), Field("project", TypeRef("Project"))),
                    operations = listOf(Operation("accept"), Operation("revoke")),
                ),
                Component(
                    id = "services.invite",
                    name = "InviteService",
                    kind = ComponentKind.SERVICE,
                    ownership = Ownership(files = listOf("app/service.py")),
                    operations = listOf(Operation("create_invite")),
                ),
            ),
            edges = listOf(
                Edge("models.project", "models.invite", EdgeTargetKind.COMPONENT, EdgeKind.REFERENCES, "project"),
                Edge("models.invite", "services.invite", EdgeTargetKind.COMPONENT, EdgeKind.REFERENCES, "invite"),
            ),
        )

        val views = CodeMapProjection.fromIr(ir, selectedId = "models.invite")

        assertEquals(3, views.size)
        assertTrue(views.all { it.origin == MiniGraphPanel.NodeOrigin.CODE })

        val invite = views.single { it.id == "models.invite" }
        assertEquals("Invite", invite.title)
        assertEquals("CODE", invite.status)
        assertEquals("model", invite.kind)
        assertEquals("app/models.py:12", invite.source)
        assertEquals("app/models.py", invite.sourcePath)
        assertEquals(12, invite.sourceLine)
        assertEquals(listOf("models.project"), invite.dependencies)
        assertTrue(invite.selected)
        assertEquals(listOf("id: str", "project: Project"), invite.fields)
        assertEquals(listOf("accept()", "revoke()"), invite.methods)
        assertTrue(invite.relationshipHint.contains("edges:"))
        assertTrue(invite.relationshipHint.contains("project"))
        assertTrue(invite.detail.contains("Current code entity"))
        assertTrue(invite.preview.contains("project"))
    }

    @Test
    fun `workflow badge stays secondary on code map views`() {
        val ir = ArchitectureIR(
            components = listOf(
                Component(
                    id = "models.invite",
                    name = "Invite",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("app/models.py")),
                ),
            ),
        )

        val views = CodeMapProjection.fromIr(
            ir = ir,
            selectedId = null,
            workflowByComponentId = mapOf(
                "models.invite" to CodeMapProjection.WorkflowBadge(
                    status = "APPLIED",
                    ready = false,
                    blocked = false,
                    selected = true,
                )
            ),
        )

        val invite = views.single()
        assertEquals(MiniGraphPanel.NodeOrigin.CODE, invite.origin)
        assertEquals("APPLIED", invite.status)
        assertTrue(invite.selected)
    }

    @Test
    fun `projection exposes truncation counts for crowded cards`() {
        val ir = ArchitectureIR(
            components = listOf(
                Component(
                    id = "models.bank",
                    name = "Bank",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("app/models.py")),
                    fields = listOf(
                        Field("id", TypeRef("str")),
                        Field("name", TypeRef("str")),
                        Field("balance", TypeRef("float")),
                        Field("currency", TypeRef("str")),
                    ),
                    operations = listOf(
                        Operation("deposit"),
                        Operation("withdraw"),
                        Operation("freeze"),
                    ),
                ),
            ),
        )

        val bank = CodeMapProjection.fromIr(ir, selectedId = null).single()

        assertEquals(listOf("id: str", "name: str", "balance: float"), bank.fields)
        assertEquals(1, bank.fieldOverflowCount)
        assertEquals(listOf("deposit()", "withdraw()"), bank.methods)
        assertEquals(1, bank.methodOverflowCount)
    }
}
