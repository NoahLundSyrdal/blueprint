package com.blueprint.ui

import com.blueprint.ir.ArchitectureIR
import com.blueprint.ir.Component
import com.blueprint.ir.ComponentKind
import com.blueprint.ir.Edge
import com.blueprint.ir.EdgeKind
import com.blueprint.ir.EdgeTargetKind
import com.blueprint.ir.Field
import com.blueprint.ir.Module
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

    @Test
    fun `projection can group code entities by package modules`() {
        val ir = ArchitectureIR(
            modules = listOf(
                Module("app.models", "app.models", "app/models/", componentIds = listOf("app.models.invite")),
                Module("app.services", "app.services", "app/services/", componentIds = listOf("app.services.invite")),
            ),
            components = listOf(
                Component(
                    id = "app.models.invite",
                    name = "Invite",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("app/models/invite.py")),
                ),
                Component(
                    id = "app.services.invite",
                    name = "InviteService",
                    kind = ComponentKind.SERVICE,
                    ownership = Ownership(files = listOf("app/services/invite.py")),
                ),
            ),
        )

        val views = CodeMapProjection.fromIr(
            ir = ir,
            selectedId = null,
            options = CodeMapProjection.Options(groupMode = CodeMapProjection.GroupMode.PACKAGE),
        )

        assertEquals("Package: app.models", views.single { it.id == "app.models.invite" }.groupTitle)
        assertEquals("Package: app.services", views.single { it.id == "app.services.invite" }.groupTitle)
        assertTrue(views.map { it.groupOrder }.toSet().size == 2)
    }

    @Test
    fun `projection can group code entities by architectural layer`() {
        val ir = ArchitectureIR(
            components = listOf(
                Component(
                    id = "app.routes.create_invite",
                    name = "create_invite",
                    kind = ComponentKind.SERVICE,
                    tags = setOf("route"),
                    ownership = Ownership(files = listOf("app/routes.py")),
                ),
                Component(
                    id = "app.service.invite",
                    name = "InviteService",
                    kind = ComponentKind.SERVICE,
                    ownership = Ownership(files = listOf("app/service.py")),
                ),
                Component(
                    id = "app.models.invite",
                    name = "Invite",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("app/models.py")),
                ),
                Component(
                    id = "tests.test_invite",
                    name = "test_invite",
                    kind = ComponentKind.TEST,
                    ownership = Ownership(files = listOf("tests/test_invite.py")),
                ),
            ),
        )

        val views = CodeMapProjection.fromIr(
            ir = ir,
            selectedId = null,
            options = CodeMapProjection.Options(groupMode = CodeMapProjection.GroupMode.LAYER),
        )

        assertEquals("Layer: API", views.single { it.id == "app.routes.create_invite" }.groupTitle)
        assertEquals("Layer: Service", views.single { it.id == "app.service.invite" }.groupTitle)
        assertEquals("Layer: Model", views.single { it.id == "app.models.invite" }.groupTitle)
        assertEquals("Layer: Test", views.single { it.id == "tests.test_invite" }.groupTitle)
        assertTrue(
            views.single { it.id == "app.routes.create_invite" }.groupOrder <
                views.single { it.id == "app.models.invite" }.groupOrder,
        )
    }

    @Test
    fun `projection filters tests generated nodes and noisy edges`() {
        val ir = ArchitectureIR(
            components = listOf(
                Component(
                    id = "app.models.invite",
                    name = "Invite",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("app/models.py")),
                ),
                Component(
                    id = "app.service.invite",
                    name = "InviteService",
                    kind = ComponentKind.SERVICE,
                    ownership = Ownership(files = listOf("app/service.py")),
                ),
                Component(
                    id = "tests.test_invite",
                    name = "test_invite",
                    kind = ComponentKind.TEST,
                    ownership = Ownership(files = listOf("tests/test_invite.py")),
                ),
                Component(
                    id = "imported.invite.models.invitepolicy",
                    name = "InvitePolicy",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("blueprint_demo/imported_invite/models.py")),
                ),
            ),
            edges = listOf(
                Edge(
                    from = "app.service.invite",
                    to = "app.models.invite",
                    toKind = EdgeTargetKind.COMPONENT,
                    kind = EdgeKind.REFERENCES,
                    label = "uses",
                    evidence = "typed parameter",
                    confidence = 0.95,
                ),
                Edge(
                    from = "tests.test_invite",
                    to = "app.service.invite",
                    toKind = EdgeTargetKind.COMPONENT,
                    kind = EdgeKind.CALLS,
                    label = "tests",
                    evidence = "call expression: InviteService",
                    confidence = 0.8,
                ),
                Edge(
                    from = "app.models.invite",
                    to = "app.service.invite",
                    toKind = EdgeTargetKind.COMPONENT,
                    kind = EdgeKind.REFERENCES,
                    label = "import",
                    evidence = "imported symbol: InviteService",
                    confidence = 0.65,
                ),
            ),
        )

        val views = CodeMapProjection.fromIr(
            ir = ir,
            selectedId = null,
            options = CodeMapProjection.Options(
                groupMode = CodeMapProjection.GroupMode.PACKAGE,
                hideTests = true,
                hideGenerated = true,
                hideExternalEdges = true,
                hideLowConfidenceEdges = true,
            ),
        )

        assertEquals(setOf("app.models.invite", "app.service.invite"), views.map { it.id }.toSet())
        assertEquals(listOf("app.service.invite"), views.single { it.id == "app.models.invite" }.dependencies)
        assertTrue(views.single { it.id == "app.service.invite" }.dependencies.isEmpty())
    }
}
