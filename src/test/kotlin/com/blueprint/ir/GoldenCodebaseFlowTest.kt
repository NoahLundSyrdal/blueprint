package com.blueprint.ir

import com.blueprint.model.NodeContract
import com.blueprint.model.Patch
import com.blueprint.service.DiskSnapshot
import com.blueprint.service.PatchFreshness
import com.blueprint.service.PythonModelModuleRenderer
import com.blueprint.service.UmlImportService
import com.intellij.openapi.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class GoldenCodebaseFlowTest {
    @Test
    fun `golden codebase fixture recovers real python structures into uml-ready ir`() {
        val fixture = Path.of("src/test/resources/golden_python_codebase").toAbsolutePath().normalize()
        val files = pythonFiles(fixture)
        val parsed = PythonAstParser.parse(fixture, files)
        assumeTrue("python3 is required for golden codebase extraction", parsed.usedAst)

        val symbols = parsed.symbols
        val names = symbols.map { it.name }.toSet()
        assertTrue(
            "fixture should cover models, repository, service, route handlers, and tests",
            names.containsAll(
                setOf(
                    "User",
                    "Project",
                    "Invite",
                    "ProjectRecord",
                    "InviteRecord",
                    "AuditRecord",
                    "InviteRepository",
                    "InviteService",
                    "create_invite_endpoint",
                    "test_create_invite_flow",
                ),
            ),
        )

        val invite = symbols.single { it.name == "Invite" }
        assertEquals("app/models.py", invite.relPath)
        assertTrue(invite.decorators.contains("dataclass"))
        assertTrue(invite.fields.any { it.name == "project" && it.type == "Project" })
        assertTrue(invite.fields.any { it.name == "inviter" && it.type == "User" })
        assertTrue("source line should be preserved", invite.line > 0)

        val inviteRecord = symbols.single { it.name == "InviteRecord" }
        assertTrue(inviteRecord.bases.contains("Base"))
        assertTrue(inviteRecord.fields.any { it.name == "project" && it.type.contains("ProjectRecord") })
        assertTrue(inviteRecord.fields.any { it.name == "audits" && it.type.contains("AuditRecord") })

        val route = symbols.single { it.name == "create_invite_endpoint" }
        assertEquals("FUNCTION", route.symbolKind)
        assertTrue(route.routePaths.contains("'/projects/{project_id}/invites'"))
        assertTrue(route.calls.contains("create_invite"))
        assertTrue(route.methods.single().params.any { it.name == "service" && it.type == "InviteService" })

        val testFlow = symbols.single { it.name == "test_create_invite_flow" }
        assertTrue(testFlow.imports.contains("InviteService"))
        assertTrue(testFlow.calls.contains("InviteService"))
        assertTrue(testFlow.calls.contains("create_invite"))

        val ir = fixtureIrFromSymbols(projectName = "golden-invite", symbols = symbols)
        val componentsByName = ir.components.associateBy { it.name }
        assertEquals(ComponentKind.MODEL, componentsByName.getValue("Invite").kind)
        assertEquals(ComponentKind.ADAPTER, componentsByName.getValue("InviteRepository").kind)
        assertEquals(ComponentKind.SERVICE, componentsByName.getValue("InviteService").kind)
        assertEquals(ComponentKind.SERVICE, componentsByName.getValue("create_invite_endpoint").kind)
        assertEquals(ComponentKind.TEST, componentsByName.getValue("test_create_invite_flow").kind)
        assertEquals(SourceRef("app/models.py", invite.line), componentsByName.getValue("Invite").sourceRef)
        assertTrue(ir.modules.any { it.id == "app" && it.componentIds.contains(componentsByName.getValue("InviteService").id) })
        assertTrue(ir.edges.any { edge ->
            edge.from == componentsByName.getValue("Invite").id &&
                edge.to == componentsByName.getValue("Project").id &&
                edge.kind == EdgeKind.REFERENCES &&
                edge.evidence.contains("field annotation")
        })
        assertTrue(ir.edges.any { edge ->
            edge.from == componentsByName.getValue("InviteService").id &&
                edge.to == componentsByName.getValue("InviteRepository").id &&
                edge.kind == EdgeKind.REFERENCES
        })
        assertTrue(ir.edges.any { edge ->
            edge.from == componentsByName.getValue("test_create_invite_flow").id &&
                edge.to == componentsByName.getValue("InviteService").id &&
                edge.label == "tests"
        })

        val mermaid = MermaidProjection.render(ir, "golden-invite", filesScanned = files.size)
        assertTrue(mermaid.contains("class Invite {"))
        assertTrue(mermaid.contains("project: Project"))
        assertTrue(mermaid.contains("class InviteService {"))
        assertTrue(mermaid.contains("+create_invite()"))
        assertTrue(mermaid.contains("class create_invite_endpoint {"))
        assertTrue(mermaid.contains("InviteService --> InviteRepository"))
        assertTrue(mermaid.contains("test_create_invite_flow --> InviteService : tests"))
    }

    @Test
    fun `golden uml change can generate patch apply to blueprint demo and refresh from code`() {
        val dir = Files.createTempDirectory("blueprint-golden-roundtrip")
        val generatedDir = dir.resolve("blueprint_demo/imported_invite")
        generatedDir.createDirectories()
        val models = generatedDir.resolve("models.py")
        models.writeText(
            """
            from dataclasses import dataclass

            @dataclass
            class Invite:
                id: str
                email: str
            """.trimIndent(),
        )

        val beforeParse = PythonAstParser.parse(dir, listOf(models))
        assumeTrue("python3 is required for golden round-trip extraction", beforeParse.usedAst)
        assertTrue(beforeParse.symbols.single { it.name == "Invite" }.fields.none { it.name == "expires_at" })

        val uml = """
            classDiagram
            class Invite {
              id: str
              email: str
              expires_at: datetime
            }
            class InviteAuditLog {
              actor_email: str
              action: str
              created_at: datetime
              reason: str
              invite: Invite
            }
            class InvitePolicy {
              max_invites: int
              domain: str
              expires_on: date
            }
            InviteAuditLog --> Invite : references
            Invite --> InvitePolicy : references
        """.trimIndent()
        val parsedUml = UmlImportService(fakeProject("golden-invite")).parse(uml)
        val umlIr = UmlToIR.toIR(parsedUml, "golden-invite")
        val generatedModels = renderDataclasses(umlIr, existingContent = Files.readString(models))
        assertTrue(generatedModels.contains("from datetime import date, datetime"))
        val patch = Patch(
            path = "blueprint_demo/imported_invite/models.py",
            action = "update",
            content = generatedModels,
        )
        val before = DiskSnapshot(exists = true, content = Files.readString(models))
        models.writeText(generatedModels)
        val after = DiskSnapshot(exists = true, content = Files.readString(models))
        val freshness = PatchFreshness.verify(patch, before, after)
        assertTrue(freshness.matchesPatch)
        assertTrue(freshness.changedDisk)

        val refreshed = PythonAstParser.parse(dir, listOf(models))
        assumeTrue("python3 is required for golden round-trip refresh", refreshed.usedAst)
        val invite = refreshed.symbols.single { it.name == "Invite" }
        val audit = refreshed.symbols.single { it.name == "InviteAuditLog" }
        val policy = refreshed.symbols.single { it.name == "InvitePolicy" }
        assertTrue(invite.fields.any { it.name == "expires_at" && it.type == "datetime" })
        assertTrue(audit.fields.any { it.name == "reason" && it.type == "str" })
        assertTrue(audit.fields.any { it.name == "invite" && it.type == "Invite" })
        assertTrue(policy.fields.any { it.name == "max_invites" && it.type == "int" })
        assertTrue(policy.fields.any { it.name == "expires_on" && it.type == "date" })

        val refreshedIr = fixtureIrFromSymbols("golden-invite", refreshed.symbols)
        assertTrue(refreshedIr.edges.any { edge ->
            edge.from.endsWith(".inviteauditlog") &&
                edge.to.endsWith(".invite") &&
                edge.kind == EdgeKind.REFERENCES
        })
    }

    private fun pythonFiles(root: Path): List<Path> =
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".py") }
                .toList()
                .sorted()
        }

    private fun fixtureIrFromSymbols(
        projectName: String,
        symbols: List<PythonAstParser.Symbol>,
    ): ArchitectureIR {
        val components = symbols.map { symbol -> symbol.toFixtureComponent() }
        val componentIdsByName = components.associate { it.name to it.id }
        val edges = symbols.flatMap { symbol -> symbol.toFixtureEdges(componentIdsByName) }
            .distinctBy { "${it.from}|${it.to}|${it.kind}|${it.label}" }
        val modules = components
            .groupBy { component ->
                component.ownership.files.firstOrNull()
                    ?.substringBeforeLast("/", "")
                    ?.ifBlank { "." }
                    ?: "."
            }
            .map { (path, moduleComponents) ->
                val normalized = if (path == ".") "." else path
                Module(
                    id = normalized.replace('/', '.'),
                    name = normalized,
                    path = if (normalized == ".") "." else "$normalized/",
                    componentIds = moduleComponents.map { it.id }.sorted(),
                    sourceRef = moduleComponents.minByOrNull { it.sourceRef?.line ?: Int.MAX_VALUE }?.sourceRef,
                    description = "Recovered fixture module $normalized",
                )
            }
            .sortedBy { it.id }

        return ArchitectureIR(
            project = ProjectMeta(name = projectName, sourceRoots = listOf("app")),
            modules = modules,
            components = components,
            dataTypes = components
                .filter { it.kind == ComponentKind.MODEL }
                .map { component ->
                    DataType(
                        id = "${component.id}.data",
                        name = component.name,
                        kind = if (component.tags.contains("dataclass")) DataKind.DATACLASS else DataKind.PYDANTIC,
                        fields = component.fields,
                        sourceRef = component.sourceRef,
                    )
                },
            edges = edges,
            coverage = RecoveryCoverage(
                componentsRecovered = components.size,
                componentsTotal = symbols.size,
                contractsRecovered = 0,
            ),
        )
    }

    private fun PythonAstParser.Symbol.toFixtureComponent(): Component {
        val kind = when {
            relPath.startsWith("tests/") || name.startsWith("test_") -> ComponentKind.TEST
            symbolKind == "FUNCTION" && routePaths.isNotEmpty() -> ComponentKind.SERVICE
            decorators.any { it.endsWith("dataclass") } -> ComponentKind.MODEL
            bases.any { it.endsWith("Base") || it.endsWith("BaseModel") || it.endsWith("TypedDict") } && fields.isNotEmpty() -> ComponentKind.MODEL
            name.endsWith("Repository") || name.endsWith("Record") -> ComponentKind.ADAPTER
            name.endsWith("Service") || name.endsWith("Endpoint") || name.endsWith("_endpoint") -> ComponentKind.SERVICE
            else -> ComponentKind.SERVICE
        }
        val operations = if (symbolKind == "FUNCTION") {
            methods.map { it.toOperation() }
        } else {
            methods
                .filter { !it.name.startsWith("_") || it.name == "__init__" }
                .map { it.toOperation() }
        }
        val sourceRef = SourceRef(relPath, line)
        return Component(
            id = componentId(name, relPath),
            name = name,
            kind = kind,
            concurrency = if (methods.any { it.async }) Concurrency.ASYNC else Concurrency.SYNC,
            ownership = Ownership(files = listOf(relPath)),
            fields = fields.map { Field(it.name, parseFixtureType(it.type), sourceRef = sourceRef) },
            operations = operations,
            requires = methods.flatMap { method ->
                method.params.mapNotNull { param ->
                    param.type.cleanTypeName().takeIf { it.firstOrNull()?.isUpperCase() == true }
                }
            }.distinct(),
            tags = buildSet {
                decorators.forEach { decorator ->
                    if (decorator.endsWith("dataclass")) add("dataclass")
                }
                if (routePaths.isNotEmpty()) add("route")
            },
            sourceRef = sourceRef,
            description = "Recovered fixture symbol from $relPath",
        )
    }

    private fun PythonAstParser.Symbol.toFixtureEdges(componentIdsByName: Map<String, String>): List<Edge> {
        val from = componentId(name, relPath)
        val edges = mutableListOf<Edge>()

        fun add(targetName: String, kind: EdgeKind, label: String, evidence: String, confidence: Double) {
            val target = componentIdsByName[targetName] ?: return
            if (target == from) return
            edges += Edge(
                from = from,
                to = target,
                kind = kind,
                label = label,
                sourceRef = SourceRef(relPath, line),
                evidence = evidence,
                confidence = confidence,
            )
        }

        fields.forEach { field ->
            componentIdsByName.keys
                .filter { other -> other != name && field.type.contains(other) }
                .forEach { other ->
                    val kind = if (field.name.endsWith("s")) EdgeKind.CONTAINS else EdgeKind.REFERENCES
                    add(other, kind, "${field.name} field", "field annotation: ${field.name}: ${field.type}", 0.95)
                }
        }
        methods.flatMap { it.params }.forEach { param ->
            add(param.type.cleanTypeName(), EdgeKind.REFERENCES, "${param.name} constructor param", "typed parameter: ${param.name}: ${param.type}", 0.9)
        }
        imports.forEach { imported ->
            add(imported.substringAfterLast("."), EdgeKind.REFERENCES, "import", "imported symbol: $imported", 0.65)
        }
        calls.forEach { call ->
            val targetName = call.substringAfterLast(".")
            val label = if (relPath.startsWith("tests/") || name.startsWith("test_")) "tests" else "calls"
            add(targetName, EdgeKind.CALLS, label, "call expression: $call", 0.8)
        }
        return edges
    }

    private fun PythonAstParser.MethodDecl.toOperation(): Operation =
        Operation(
            name = name,
            params = params.filterNot { it.name == "self" || it.name == "cls" }
                .map { Param(it.name, parseFixtureType(it.type), it.default) },
            returns = parseFixtureType(returns),
            concurrency = if (async) Concurrency.ASYNC else Concurrency.SYNC,
            sourceRef = SourceRef("", line),
        )

    private fun renderDataclasses(ir: ArchitectureIR, existingContent: String): String =
        PythonModelModuleRenderer.render(
            ir.components
                .filter { it.kind == ComponentKind.MODEL }
                .map { component ->
                    NodeContract(
                        name = component.name,
                        kind = "schema",
                        description = "Data model ${component.name}",
                        schema = component.fields.joinToString("\n") { field -> "${field.name}: ${field.type.id}" },
                    )
                },
            existingContent = existingContent,
        )

    private fun componentId(name: String, relPath: String): String =
        relPath.removeSuffix(".py").replace('/', '.').trim('.').let { module ->
            if (module.isBlank()) name.lowercase() else "$module.${name.lowercase()}"
        }

    private fun parseFixtureType(raw: String): TypeRef =
        TypeRef(raw.cleanTypeName().ifBlank { "Any" })

    private fun String.cleanTypeName(): String =
        trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .substringBefore("[")
            .substringAfterLast(".")
            .ifBlank { "Any" }

    private fun fakeProject(name: String): Project =
        Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getName" -> name
                "isDisposed" -> false
                "getBasePath" -> null
                "toString" -> "FixtureProject($name)"
                "hashCode" -> name.hashCode()
                "equals" -> false
                else -> defaultValue(method.returnType)
            }
        } as Project

    private fun defaultValue(type: Class<*>): Any? =
        when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Void.TYPE -> null
            else -> null
        }
}
