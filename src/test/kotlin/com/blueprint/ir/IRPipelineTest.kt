package com.blueprint.ir

import com.blueprint.model.NodeType
import com.blueprint.service.UmlImportService
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the pure IR pipeline without any Project dependency.
 *
 * Covers: UML → IR, IR → Nodes (deterministic ids, scope, deps), IR → Mermaid,
 * and gson round-trip for IR persistence.
 */
class IRPipelineTest {

    // ---- UML → IR → Nodes end-to-end ----

    @Test
    fun `uml parsed into ir produces component-per-entity plus scaffolding`() {
        val parsed = UmlImportService.ParsedUml(
            entities = listOf(
                UmlImportService.ParsedEntity("Invite", listOf("id: str", "email: str")),
                UmlImportService.ParsedEntity("InvitePolicy", listOf("max_invites: int")),
            ),
            relationships = listOf(
                UmlImportService.ParsedRelationship("Invite", "belongs to", "InvitePolicy"),
            ),
            warnings = emptyList(),
        )

        val ir = UmlToIR.toIR(parsed, projectName = "testproj")

        val modelNames = ir.components.filter { it.kind == ComponentKind.MODEL }.map { it.name }.toSet()
        assertEquals(setOf("Invite", "InvitePolicy"), modelNames)

        val scaffoldKinds = ir.components.filter { it.tags.contains("scaffold") }.map { it.kind }.toSet()
        assertEquals(
            setOf(ComponentKind.SERVICE, ComponentKind.CLI, ComponentKind.TEST, ComponentKind.DOCS),
            scaffoldKinds,
        )

        // 2 models + 4 scaffolding = 6 components, not the old hardcoded 5.
        assertEquals(6, ir.components.size)

        val relationshipEdge = ir.edges.firstOrNull { it.kind == EdgeKind.CONTAINS }
        assertNotNull("belongs-to relationship should map to CONTAINS edge", relationshipEdge)
    }

    @Test
    fun `ir compiles to blueprint nodes with file scope and deterministic ids`() {
        val parsed = UmlImportService.ParsedUml(
            entities = listOf(
                UmlImportService.ParsedEntity("Invite", listOf("id: str", "email: str")),
                UmlImportService.ParsedEntity("InvitePolicy", listOf("max_invites: int")),
            ),
            relationships = emptyList(),
            warnings = emptyList(),
        )
        val ir = UmlToIR.toIR(parsed, "testproj")

        val first = IRCompilation.compile(ir)
        val second = IRCompilation.compile(ir)

        assertEquals("re-compiling same IR must yield same ids", first.nodes.map { it.id }, second.nodes.map { it.id })
        assertTrue("nodes should have file scopes", first.nodes.all { !it.fileScope.isEmpty() })
        assertEquals(ir.components.size, first.nodes.size)

        val schemaNodes = first.nodes.filter { it.type == NodeType.SCHEMA }
        assertEquals("both models become SCHEMA nodes", 2, schemaNodes.size)
        val backendNode = first.nodes.firstOrNull { it.type == NodeType.BACKEND }
        assertNotNull("scaffolding service becomes BACKEND", backendNode)
        assertTrue(
            "service depends on model nodes",
            backendNode!!.dependencies.toSet().containsAll(schemaNodes.map { it.id }),
        )
    }

    // ---- IR with async service gets async acceptance criterion ----

    @Test
    fun `async service component gets no-blocking-io acceptance criterion`() {
        val ir = ArchitectureIR(
            project = ProjectMeta(name = "asynctest"),
            components = listOf(
                Component(
                    id = "svc.async",
                    name = "AsyncPaymentService",
                    kind = ComponentKind.SERVICE,
                    concurrency = Concurrency.ASYNC,
                    ownership = Ownership(files = listOf("payments/async_service.py")),
                    operations = listOf(
                        Operation(
                            name = "authorize",
                            params = listOf(Param("request", TypeRef("PaymentRequest"))),
                            returns = TypeRef("PaymentResult"),
                            concurrency = Concurrency.ASYNC,
                        )
                    ),
                )
            ),
        )

        val result = IRCompilation.compile(ir)
        val node = result.nodes.single()
        val asyncAc = node.acceptanceCriteria.firstOrNull {
            it.description.contains("async def", ignoreCase = true) ||
                it.description.contains("blocking I/O", ignoreCase = true)
        }
        assertNotNull("expected async/non-blocking acceptance criterion", asyncAc)
        assertEquals("async", node.metadata["concurrency"])
    }

    // ---- Forbidden patterns propagate to invariants ----

    @Test
    fun `forbidden patterns surface as invariants and policy acceptance`() {
        val ir = ArchitectureIR(
            project = ProjectMeta(name = "polytest"),
            components = listOf(
                Component(
                    id = "svc.strict",
                    name = "StrictService",
                    kind = ComponentKind.SERVICE,
                    ownership = Ownership(files = listOf("strict/service.py")),
                    forbiddenPatterns = setOf("global_state", "hidden_network_calls"),
                )
            ),
        )
        val node = IRCompilation.compile(ir).nodes.single()
        assertTrue(node.invariants.any { it.contains("global_state") })
        assertTrue(node.acceptanceCriteria.any { it.id == "AC_POLICY" })
    }

    // ---- Mermaid projection ----

    @Test
    fun `mermaid projector emits classDiagram with components and edges`() {
        val parsed = UmlImportService.ParsedUml(
            entities = listOf(
                UmlImportService.ParsedEntity("Invite", listOf("id: str")),
                UmlImportService.ParsedEntity("InvitePolicy", listOf("max_invites: int")),
            ),
            relationships = listOf(UmlImportService.ParsedRelationship("Invite", "belongs to", "InvitePolicy")),
            warnings = emptyList(),
        )
        val ir = UmlToIR.toIR(parsed, "testproj")
        val mermaid = MermaidProjection.render(ir, "testproj", filesScanned = 3)

        assertTrue("must start with classDiagram", mermaid.startsWith("classDiagram"))
        assertTrue("must mention Invite", mermaid.contains("class Invite"))
        assertTrue("must mention InvitePolicy", mermaid.contains("class InvitePolicy"))
        assertTrue("must render relationship edge label", mermaid.contains("belongs to"))
    }

    @Test
    fun `mermaid handles empty ir gracefully`() {
        val ir = ArchitectureIR(project = ProjectMeta(name = "empty"))
        val mermaid = MermaidProjection.render(ir, "empty", filesScanned = 0)
        assertTrue(mermaid.startsWith("classDiagram"))
        assertTrue(mermaid.contains("No components recovered"))
    }

    // ---- Gson round-trip ----

    @Test
    fun `gson round trip preserves all structural fields`() {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val original = ArchitectureIR(
            project = ProjectMeta(name = "gsontest", sourceRoots = listOf("src", "lib")),
            components = listOf(
                Component(
                    id = "payments.service",
                    name = "PaymentService",
                    kind = ComponentKind.SERVICE,
                    concurrency = Concurrency.ASYNC,
                    provides = listOf("payments.Authorizer"),
                    requires = listOf("payments.Gateway"),
                    ownership = Ownership(files = listOf("payments/service.py"), globs = listOf("payments/**.py")),
                    operations = listOf(
                        Operation(
                            "authorize",
                            listOf(Param("req", TypeRef("PaymentRequest"))),
                            TypeRef("PaymentResult"),
                            concurrency = Concurrency.ASYNC,
                        ),
                    ),
                    forbiddenPatterns = setOf("global_state"),
                    opaqueAnnotations = listOf(
                        OpaqueAnnotation("@my_weird_decorator", SourceRef("payments/service.py", 42), "custom decorator"),
                    ),
                ),
            ),
            contracts = listOf(
                Contract(
                    id = "payments.Authorizer",
                    name = "Authorizer",
                    kind = ContractKind.INTERFACE,
                    concurrency = Concurrency.ASYNC,
                    operations = listOf(
                        Operation("authorize", emptyList(), TypeRef("PaymentResult"), concurrency = Concurrency.ASYNC),
                    ),
                ),
            ),
            edges = listOf(
                Edge(
                    "payments.service",
                    "payments.gateway",
                    EdgeTargetKind.COMPONENT,
                    EdgeKind.CALLS,
                    "authorize",
                    SourceRef("payments/service.py", 42),
                    "call expression: gateway.authorize",
                    0.8,
                ),
            ),
            coverage = RecoveryCoverage(componentsRecovered = 1, componentsTotal = 1, contractsRecovered = 1),
        )

        val json = gson.toJson(original)
        val back = gson.fromJson(json, ArchitectureIR::class.java)

        assertEquals(original.project.name, back.project.name)
        assertEquals(original.components.size, back.components.size)
        val c = back.components.single()
        assertEquals(ComponentKind.SERVICE, c.kind)
        assertEquals(Concurrency.ASYNC, c.concurrency)
        assertEquals(listOf("payments/service.py"), c.ownership.files)
        assertEquals(setOf("global_state"), c.forbiddenPatterns)
        assertEquals(1, c.opaqueAnnotations.size)
        assertEquals(Concurrency.ASYNC, back.contracts.single().operations.single().concurrency)
        assertEquals(EdgeKind.CALLS, back.edges.single().kind)
        assertEquals("call expression: gateway.authorize", back.edges.single().evidence)
        assertEquals(0.8, back.edges.single().confidence, 0.001)
    }

    // ---- Empty IR guard ----

    @Test
    fun `compile on empty ir returns warning not crash`() {
        val result = IRCompilation.compile(ArchitectureIR(project = ProjectMeta(name = "empty")))
        assertTrue(result.nodes.isEmpty())
        assertFalse(result.warnings.isEmpty())
    }
}
