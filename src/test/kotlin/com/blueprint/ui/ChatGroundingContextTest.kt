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
import com.blueprint.ir.Param
import com.blueprint.ir.SourceRef
import com.blueprint.ir.TypeRef
import com.blueprint.service.UmlImportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGroundingContextTest {
    @Test
    fun `selected code entity includes source fields methods and relationships`() {
        val grounding = ChatGroundingContext.build(
            ir = inviteIr(),
            parsedUml = null,
            selectedCanvasId = "app.models.invite",
            viewingUmlDraft = false,
        )

        assertEquals("current code map", grounding.mode)
        assertEquals("app.models.invite", grounding.selectedId)
        assertTrue(grounding.promptText.contains("Selected code entity: Invite"))
        assertTrue(grounding.promptText.contains("Source: app/models.py:12"))
        assertTrue(grounding.promptText.contains("- project: Project"))
        assertTrue(grounding.promptText.contains("- accept() -> None"))
        assertTrue(grounding.promptText.contains("out references Project"))
        assertTrue(grounding.summaryText.contains("Grounding: selected code entity Invite"))
        assertTrue(grounding.summaryText.contains("Source file: app/models.py:12"))
        assertTrue(grounding.activityLabel.contains("selected code entity Invite"))
    }

    @Test
    fun `selected proposed uml entity includes fields and edges`() {
        val grounding = ChatGroundingContext.build(
            ir = inviteIr(),
            parsedUml = parsedUml(),
            selectedCanvasId = "InvitePolicy",
            viewingUmlDraft = true,
        )

        assertEquals("editable UML draft", grounding.mode)
        assertEquals("InvitePolicy", grounding.selectedId)
        assertTrue(grounding.promptText.contains("Selected proposed UML entity: InvitePolicy"))
        assertTrue(grounding.promptText.contains("- max_invites: int"))
        assertTrue(grounding.promptText.contains("- Project owns InvitePolicy"))
        assertTrue(grounding.summaryText.contains("Grounding: selected UML entity InvitePolicy"))
        assertTrue(grounding.summaryText.contains("Source file: not code-backed yet"))
        assertTrue(grounding.activityLabel.contains("selected UML entity InvitePolicy"))
    }

    @Test
    fun `no selection summarizes available context compactly`() {
        val grounding = ChatGroundingContext.build(
            ir = inviteIr(),
            parsedUml = parsedUml(),
            selectedCanvasId = null,
            viewingUmlDraft = false,
        )

        assertNull(grounding.selectedId)
        assertTrue(grounding.promptText.contains("Selected entity: none"))
        assertTrue(grounding.promptText.contains("UML entities in editor: Project, InvitePolicy"))
        assertTrue(grounding.promptText.contains("Code entities recovered: Invite, Project"))
        assertTrue(grounding.summaryText.contains("Grounding: whole-diagram context"))
        assertTrue(grounding.summaryText.contains("Hint: select a UML card for a more targeted chat edit."))
        assertTrue(grounding.activityLabel.contains("whole-diagram context"))
    }

    @Test
    fun `compact uml clips long diagrams`() {
        val uml = buildString {
            appendLine("classDiagram")
            repeat(100) { appendLine("class Entity$it") }
        }

        val compact = ChatGroundingContext.compactUml(uml, maxLines = 10)

        assertTrue(compact.lines().size <= 11)
        assertTrue(compact.contains("more line(s) omitted"))
    }

    private fun inviteIr(): ArchitectureIR =
        ArchitectureIR(
            components = listOf(
                Component(
                    id = "app.models.invite",
                    name = "Invite",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("app/models.py")),
                    sourceRef = SourceRef("app/models.py", 12),
                    fields = listOf(
                        Field("id", TypeRef("str")),
                        Field("project", TypeRef("Project")),
                    ),
                    operations = listOf(
                        Operation("accept"),
                        Operation("send", params = listOf(Param("email", TypeRef("str")))),
                    ),
                ),
                Component(
                    id = "app.models.project",
                    name = "Project",
                    kind = ComponentKind.MODEL,
                    ownership = Ownership(files = listOf("app/models.py")),
                    fields = listOf(Field("id", TypeRef("str"))),
                ),
            ),
            edges = listOf(
                Edge(
                    from = "app.models.invite",
                    to = "app.models.project",
                    toKind = EdgeTargetKind.COMPONENT,
                    kind = EdgeKind.REFERENCES,
                    label = "project field",
                ),
            ),
        )

    private fun parsedUml(): UmlImportService.ParsedUml =
        UmlImportService.ParsedUml(
            entities = listOf(
                UmlImportService.ParsedEntity("Project", listOf("id: str", "name: str")),
                UmlImportService.ParsedEntity("InvitePolicy", listOf("max_invites: int", "domain: str")),
            ),
            relationships = listOf(
                UmlImportService.ParsedRelationship("Project", "owns", "InvitePolicy"),
            ),
            warnings = emptyList(),
        )
}
