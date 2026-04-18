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
                ),
                Component(
                    id = "models.invite",
                    name = "Invite",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("app/models.py")),
                    fields = listOf(Field("id", TypeRef("str")), Field("project", TypeRef("Project"))),
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
        assertEquals("app/models.py", invite.source)
        assertEquals(listOf("models.project"), invite.dependencies)
        assertTrue(invite.selected)
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
}
