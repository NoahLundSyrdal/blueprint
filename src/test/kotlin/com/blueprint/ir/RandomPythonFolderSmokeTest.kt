package com.blueprint.ir

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RandomPythonFolderSmokeTest {
    @Test
    fun `flat random python folder refreshes into useful uml`() {
        val fixture = Path.of("src/test/resources/random_python_flat_tool").toAbsolutePath().normalize()
        val files = pythonFiles(fixture)
        val parsed = PythonAstParser.parse(fixture, files)
        assumeTrue("python3 is required for random-folder extraction", parsed.usedAst)

        val symbols = parsed.symbols
        assertTrue(symbols.any { it.name == "TaskConfig" && it.decorators.contains("define") })
        assertTrue(symbols.any { it.name == "TaskRunner" })
        assertTrue(symbols.any { it.name == "test_runner_uses_config_owner_email" })

        val mermaid = MermaidProjection.render(fixtureIrFromSymbols("random-flat-tool", symbols), "random-flat-tool", files.size)
        assertTrue(mermaid.contains("class TaskConfig {"))
        assertTrue(mermaid.contains("class TaskRunner {"))
    }

    @Test
    fun `namespace app random folder refreshes into useful uml`() {
        val fixture = Path.of("src/test/resources/random_python_namespace_app").toAbsolutePath().normalize()
        val files = pythonFiles(fixture)
        val parsed = PythonAstParser.parse(fixture, files)
        assumeTrue("python3 is required for random-folder extraction", parsed.usedAst)

        val symbols = parsed.symbols
        assertTrue(symbols.any { it.name == "InventoryItem" })
        assertTrue(symbols.any { it.name == "InventoryService" })
        assertTrue(symbols.any { it.name == "update_inventory" && it.routePaths.isNotEmpty() })

        val mermaid = MermaidProjection.render(fixtureIrFromSymbols("random-namespace-app", symbols), "random-namespace-app", files.size)
        assertTrue(mermaid.contains("class InventoryItem {"))
        assertTrue(mermaid.contains("class InventoryService {"))
        assertTrue(mermaid.contains("class update_inventory {"))
    }

    @Test
    fun `setup cfg src package random folder refreshes into useful uml`() {
        val fixture = Path.of("src/test/resources/random_python_setup_cfg_pkg").toAbsolutePath().normalize()
        val files = pythonFiles(fixture)
        val parsed = PythonAstParser.parse(fixture, files)
        assumeTrue("python3 is required for random-folder extraction", parsed.usedAst)

        val symbols = parsed.symbols
        assertTrue(symbols.any { it.name == "OrderRecord" && it.decorators.contains("dataclass") })
        assertTrue(symbols.any { it.name == "OrderService" })
        assertTrue(symbols.any { it.name == "test_reopen_returns_order_record" })

        val mermaid = MermaidProjection.render(fixtureIrFromSymbols("random-setup-cfg-pkg", symbols), "random-setup-cfg-pkg", files.size)
        assertTrue(mermaid.contains("class OrderRecord {"))
        assertTrue(mermaid.contains("class OrderService {"))
    }

    private fun pythonFiles(root: Path): List<Path> =
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".py") }
                .toList()
                .sorted()
        }

    private fun fixtureIrFromSymbols(projectName: String, symbols: List<PythonAstParser.Symbol>): ArchitectureIR {
        val components = symbols.map { symbol ->
            val kind = when {
                symbol.relPath.startsWith("tests/") || symbol.name.startsWith("test_") -> ComponentKind.TEST
                symbol.symbolKind == "FUNCTION" && symbol.routePaths.isNotEmpty() -> ComponentKind.SERVICE
                symbol.decorators.any { it.endsWith("dataclass") || it == "define" } -> ComponentKind.MODEL
                symbol.bases.any { it.endsWith("Base") || it.endsWith("BaseModel") || it.endsWith("TypedDict") } && symbol.fields.isNotEmpty() -> ComponentKind.MODEL
                symbol.name.endsWith("Service") -> ComponentKind.SERVICE
                else -> ComponentKind.SERVICE
            }
            Component(
                id = "${symbol.relPath}:${symbol.name}".lowercase().replace('/', '.').replace(':', '.'),
                name = symbol.name,
                kind = kind,
                concurrency = if (symbol.methods.any { it.async }) Concurrency.ASYNC else Concurrency.SYNC,
                ownership = Ownership(files = listOf(symbol.relPath)),
                operations = symbol.methods.filter { !it.name.startsWith("_") || it.name == "__init__" }.map { method ->
                    Operation(
                        name = method.name,
                        params = method.params.filter { it.name != "self" }.map { Param(it.name, TypeRef(it.type.ifBlank { "Any" })) },
                        returns = method.returns.takeIf { it.isNotBlank() }?.let(::TypeRef) ?: TypeRef("None"),
                        concurrency = if (method.async) Concurrency.ASYNC else Concurrency.SYNC,
                    )
                },
                fields = symbol.fields.map { Field(it.name, TypeRef(it.type.ifBlank { "Any" })) },
                sourceRef = SourceRef(symbol.relPath, symbol.line),
                description = "Recovered from ${symbol.relPath}",
                tags = symbol.decorators.toSet(),
            )
        }
        return ArchitectureIR(
            project = ProjectMeta(name = projectName, sourceRoots = listOf("app")),
            components = components,
            coverage = RecoveryCoverage(
                componentsRecovered = components.size,
                componentsTotal = symbols.size,
                contractsRecovered = 0,
            ),
        )
    }
}
